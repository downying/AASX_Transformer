package com.aasx.transformer.upload.service;

import org.apache.poi.openxml4j.exceptions.InvalidFormatException;
import org.apache.poi.openxml4j.opc.OPCPackage;
import org.eclipse.digitaltwin.aas4j.v3.dataformat.aasx.InMemoryFile;
import org.eclipse.digitaltwin.aas4j.v3.dataformat.core.internal.visitor.AssetAdministrationShellElementWalkerVisitor;
import org.eclipse.digitaltwin.aas4j.v3.model.Environment;
import org.eclipse.digitaltwin.aas4j.v3.model.Submodel;
import org.eclipse.digitaltwin.aas4j.v3.model.SubmodelElement;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.aasx.transformer.deserializer.AASXFileDeserializer;
import com.aasx.transformer.deserializer.SHA256HashApache;
import com.aasx.transformer.upload.dto.Files;
import com.aasx.transformer.upload.dto.FilesMeta;
import com.aasx.transformer.upload.mapper.UploadMapper;

import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

@Service
@Slf4j
public class FileUploadService {

    @Autowired
    private AASXFileDeserializer aasxFileDeserializer;

    @Autowired
    private UploadMapper uploadMapper;

    @Value("${upload.path}")
    private String uploadPath;

    @Value("${download.base-url}")
    private String baseDownloadUrl;

    // 업로드된 AASX 파일 이름
    private final List<String> uploadedFileNames = new CopyOnWriteArrayList<>();
    // 업로드된 AASX 파일로부터 변환된 Environment 목록
    private final List<Environment> uploadedEnvironments = new CopyOnWriteArrayList<>();

    // ✅ AASX 파일 업로드 및 Environment 변환
    public List<Environment> uploadFiles(MultipartFile[] files) {
        List<Environment> results = new ArrayList<>();
        uploadedFileNames.clear();
        uploadedEnvironments.clear();

        for (MultipartFile file : files) {
            String fileName = file.getOriginalFilename();
            log.info("업로드된 파일 이름: {}", fileName);
            log.info("업로드된 파일 크기: {}", file.getSize());

            try {
                if (fileName == null || !fileName.toLowerCase().endsWith(".aasx")) {
                    log.warn("AASX 형식이 아니므로 스킵: {}", fileName);
                    continue;
                }

                String filePath = uploadPath + File.separator + fileName;
                File destFile = new File(filePath);

                // 업로드 디렉토리 생성
                File uploadDir = new File(uploadPath);
                if (!uploadDir.exists()) {
                    uploadDir.mkdirs();
                    log.info("디렉토리 생성: {}", uploadPath);
                } else {
                    log.info("디렉토리 존재");
                }

                try (FileOutputStream fos = new FileOutputStream(destFile)) {
                    fos.write(file.getBytes());
                }
                log.info("파일 저장 후 경로: {}", destFile.getAbsolutePath());

                // AASX 파일을 Environment로 변환
                InputStream inputStream = file.getInputStream();
                Environment environment = aasxFileDeserializer.deserializeAASXFile(inputStream);

                if (environment != null) {
                    results.add(environment);
                    uploadedEnvironments.add(environment);
                    uploadedFileNames.add(fileName);
                } else {
                    if (destFile.exists()) {
                        destFile.delete();
                        log.warn("AASX 형식이 아니므로 삭제됨: {}", fileName);
                    }
                }
            } catch (Exception e) {
                log.error("파일 변환 중 오류 발생: {}", e.getMessage());
            }
        }
        return results;
    }

    // ✅ 업로드된 파일 이름 반환
    public List<String> getUploadedFiles() {
        return new ArrayList<>(uploadedFileNames);
    }

    // ✅ InMemoryFile 객체 목록을 반환 (환경 내 참조된 파일 경로 처리)
    public Map<String, List<InMemoryFile>> getInMemoryFilesFromReferencedPaths() {
        Map<String, List<InMemoryFile>> inMemoryFilesMap = new HashMap<>();
        // 업로드된 파일 목록과 environment 목록은 같은 인덱스를 가짐
        for (int i = 0; i < uploadedFileNames.size(); i++) {
            String fileName = uploadedFileNames.get(i);
            Environment environment = uploadedEnvironments.get(i);
            // Environment에서 참조된 파일 경로들 추출
            List<String> paths = aasxFileDeserializer.parseReferencedFilePathsFromAASX(environment);

            if (paths.isEmpty()) {
                // 참조 경로가 없다면 비어있는 리스트 매핑
                inMemoryFilesMap.put(fileName, Collections.emptyList());
                continue;
            }
            // 실제 디스크에 저장된 AASX 파일 열기
            String aasxFilePath = uploadPath + File.separator + fileName;
            File aasxFile = new File(aasxFilePath);
            // OPCPackage로 AASX 내부 리소스를 읽어옴
            try (OPCPackage aasxRoot = OPCPackage.open(aasxFile)) {
                List<InMemoryFile> inMemoryFiles = aasxFileDeserializer.readFiles(aasxRoot, paths);
                inMemoryFilesMap.put(fileName, inMemoryFiles);
            } catch (InvalidFormatException | IOException e) {
                log.error("AASX 내부 파일 읽기 오류 ({}): {}", fileName, e.getMessage(), e);
                inMemoryFilesMap.put(fileName, Collections.emptyList());
            }
        }
        return inMemoryFilesMap;
    }

    // ✅ 동일 파일 검색 및 해시값 기반 파일 저장 후, fileName과 업데이트된 Environment를 매핑하여 반환
    /**
     * (Phase 2) 파일 해시 계산 후 DB 반영, 물리 파일 저장, 그리고 Environment 내 File 객체의 경로를 다운로드
     * URL로 변경
     * - files 테이블에 (hash, size)를 등록 (없으면 삽입, 있으면 ref_count 증가)
     * - files_meta 테이블에는 Environment 객체에서 추출한 AAS ID, Submodel ID, idShort(복합키)와 함께
     * 파일 이름(원본 파일명에서 확장자 앞 부분)과 확장자, content type, 해시를 등록
     * - 물리 파일은 hash + extension 으로 저장되며, 다운로드 URL은
     * baseDownloadUrl + "/api/transformer/download/" + hash + extension
     * 의 형식으로 구성
     */
    public Map<String, Environment> computeSHA256HashesForInMemoryFiles() {
        Map<String, Environment> updatedEnvironmentMap = new HashMap<>();
        Map<String, List<InMemoryFile>> inMemoryFilesMap = getInMemoryFilesFromReferencedPaths();

        for (Map.Entry<String, List<InMemoryFile>> entry : inMemoryFilesMap.entrySet()) {
            String fileNameKey = entry.getKey();
            List<InMemoryFile> inMemoryFiles = entry.getValue();

            int index = uploadedFileNames.indexOf(fileNameKey);
            if (index < 0 || index >= uploadedEnvironments.size()) {
                log.warn("해당 파일 이름에 대응하는 Environment를 찾을 수 없음: {}", fileNameKey);
                continue;
            }
            Environment environment = uploadedEnvironments.get(index);

            if (inMemoryFiles.isEmpty()) {
                log.info("첨부파일이 없습니다: {}", fileNameKey);
                updatedEnvironmentMap.put(fileNameKey, environment);
                continue;
            }

            for (InMemoryFile inMemoryFile : inMemoryFiles) {
                try {
                    // 1) SHA-256 해시 계산 및 파일 크기 구하기
                    String hash = SHA256HashApache.computeSHA256Hash(inMemoryFile);
                    int fileSize = inMemoryFile.getFileContent().length;

                    // 2) DB의 files 테이블에 파일 해시 등록 (hash, size)
                    uploadMapper.insertFile(hash, fileSize);

                    // 3) Environment 객체를 통해 해당 파일의 aas_id, submodel_id, idShort 추출
                    String originalPath = inMemoryFile.getPath();
                    // Environment 기반으로 composite key를 얻음 (형식: "aasId/submodelId/idShort")
                    String compositeKey = deriveCompositeKeyFromEnvironment(environment, originalPath);
                    String[] keys = compositeKey.split("/");
                    String aasId = keys.length > 0 ? keys[0] : "unknown";
                    String submodelId = keys.length > 1 ? keys[1] : "unknown";
                    String idShort = keys.length > 2 ? keys[2] : "unknown";

                    // 4) Environment 내에서 원본 파일의 상대경로에서 파일명과 확장자 추출
                    String baseName = new File(originalPath).getName();
                    String extractedName;
                    String extension = "";
                    int dotIndex = baseName.lastIndexOf(".");
                    if (dotIndex > 0) {
                        extractedName = baseName.substring(0, dotIndex);
                        extension = baseName.substring(dotIndex); // 확장자 (점 포함)
                    } else {
                        extractedName = baseName;
                    }

                    // 5) files_meta 테이블에 파일 메타 등록
                    FilesMeta meta = new FilesMeta();
                    meta.setAasId(aasId);
                    meta.setSubmodelId(submodelId);
                    meta.setIdShort(idShort);
                    meta.setName(extractedName); // 원본 파일 이름(확장자 제외)
                    meta.setExtension(extension); // 추출한 확장자
                    meta.setContentType("application/octet-stream"); // 필요에 따라 MIME 타입 적용
                    meta.setHash(hash);
                    uploadMapper.insertFileMeta(meta);

                    // 6) 물리 파일 저장 (이미 존재하면 저장하지 않음)
                    String newFileName = hash + extension;
                    String newFilePath = uploadPath + File.separator + newFileName;
                    File newFile = new File(newFilePath);
                    if (!newFile.exists()) {
                        try (FileOutputStream fos = new FileOutputStream(newFile)) {
                            fos.write(inMemoryFile.getFileContent());
                        }
                        log.info("첨부파일 로컬 저장: {}", newFilePath);
                    } else {
                        log.info("이미 로컬에 존재: {}", newFilePath);
                    }

                    // 7) 다운로드 URL 구성: baseDownloadUrl + "/api/transformer/download/" + hash +
                    // extension
                    String hashBasedUrl = baseDownloadUrl + "/api/transformer/download/" + hash + extension;

                    // 8) Environment 내 File 객체의 값이 originalPath와 일치하면 다운로드 URL로 업데이트
                    updateEnvironmentFilePathsToURL(environment, originalPath, hashBasedUrl);

                } catch (Exception e) {
                    log.error("해시 계산 또는 DB 반영 중 오류: {}", e.getMessage(), e);
                }
            }
            updatedEnvironmentMap.put(fileNameKey, environment);
        }

        log.info("업데이트된 Environment Map의 파일 이름 목록: {}", updatedEnvironmentMap.keySet());
        return updatedEnvironmentMap;
    }

    // ✅ Environment 내 File 객체의 value 값을 원본 경로와 일치하는 경우에만 해시 기반 URL로 변경
    public void updateEnvironmentFilePathsToURL(Environment environment, String originalPath, String updatedUrl) {
        AssetAdministrationShellElementWalkerVisitor visitor = new AssetAdministrationShellElementWalkerVisitor() {
            @Override
            public void visit(org.eclipse.digitaltwin.aas4j.v3.model.File file) {
                if (file != null && file.getValue() != null) {
                    log.info("현재 file value: {}", file.getValue());
                    if (file.getValue().trim().equals(originalPath.trim())) {
                        file.setValue(updatedUrl);
                        log.info("Updated file path from {} to {}", originalPath, updatedUrl);
                    }
                }
            }

        };
        visitor.visit(environment);
    }

    /**
     * Environment 내에서 originalPath와 일치하는 File 요소를 찾아,
     * 해당 File 요소의 부모(Submodel)의 id와 File 요소의 idShort를 가져와
     * "aasId/submodelId/fileIdShort" 형식의 문자열로 반환
     */
    private String deriveCompositeKeyFromEnvironment(Environment env, String originalPath) {
        // AAS ID는 첫 번째 AAS의 ID 사용
        String aasId = env.getAssetAdministrationShells().get(0).getId();

        String submodelId = null;
        String fileIdShort = null;

        // Environment 내의 모든 Submodel을 순회하여
        // 각 Submodel의 SubmodelElements 중 File 요소의 value가 originalPath와 일치하는 File을 찾음
        if (env.getSubmodels() != null) {
            for (Submodel submodel : env.getSubmodels()) {
                if (submodel.getSubmodelElements() != null) {
                    for (SubmodelElement sme : submodel.getSubmodelElements()) {
                        if (sme instanceof org.eclipse.digitaltwin.aas4j.v3.model.File) {
                            org.eclipse.digitaltwin.aas4j.v3.model.File fileElem = (org.eclipse.digitaltwin.aas4j.v3.model.File) sme;
                            // 첨부파일의 상대경로가 일치하는지 비교
                            if (fileElem.getValue() != null && fileElem.getValue().equals(originalPath)) {
                                submodelId = submodel.getId(); // 해당 File이 속한 Submodel의 ID
                                fileIdShort = fileElem.getIdShort(); // 해당 File 요소의 idShort
                                break;
                            }
                        }
                    }
                }
                if (submodelId != null) {
                    break;
                }
            }
        }

        return aasId + "/" + submodelId + "/" + fileIdShort;
    }

    /**
     * 파일 메타 삭제 (클라이언트 요청 시)
     * DB에서 파일 메타를 삭제한 후 해당 해시의 ref_count를 감소시키고, 0이면 files 테이블에서도 삭제
     */
    public void deleteFileMeta(String compositeKey) {
        String[] keys = compositeKey.split("/");
        if (keys.length < 3) {
            log.warn("파일 메타 삭제를 위한 key 형식이 올바르지 않음: {}", compositeKey);
            return;
        }
        String aasId = keys[0];
        String submodelId = keys[1];
        String idShort = keys[2];
        FilesMeta meta = uploadMapper.selectFileMetaByKey(aasId, submodelId, idShort);
        if (meta == null) {
            log.warn("파일 메타가 존재하지 않음: {}", compositeKey);
            return;
        }
        String hash = meta.getHash();
        uploadMapper.deleteFileMeta(aasId, submodelId, idShort);
        log.info("파일 메타 삭제됨: {}", compositeKey);
        uploadMapper.decrementFileRefCount(hash);
        Files fileInfo = uploadMapper.selectFileByHash(hash);
        if (fileInfo == null || fileInfo.getRefCount() <= 0) {
            uploadMapper.deleteFileByHash(hash);
            log.info("ref_count 0으로 인해 files 테이블에서도 삭제됨: {}", hash);
        }
    }

    /**
     * 특정 패키지 파일 이름에 해당하는 Environment의 첨부파일 메타 정보를 조회합니다.
     * Environment 내의 각 Submodel을 순회하여 File 요소를 확인하고,
     * 각 File 요소의 복합키 (aasId, submodelId, idShort)를 이용해 DB에서 파일 메타 정보를 조회합니다.
     */
    public List<FilesMeta> getFileMetasByPackageFileName(String packageFileName) {
        int index = uploadedFileNames.indexOf(packageFileName);
        if (index < 0) {
            log.warn("패키지 파일 '{}' 에 해당하는 Environment를 찾을 수 없습니다.", packageFileName);
            return Collections.emptyList();
        }
        Environment environment = uploadedEnvironments.get(index);
        List<FilesMeta> metas = new ArrayList<>();

        // 기본적으로 Environment의 첫번째 AAS의 ID를 사용 (필요에 따라 수정 가능)
        String aasId = environment.getAssetAdministrationShells().get(0).getId();

        if (environment.getSubmodels() != null) {
            for (Submodel submodel : environment.getSubmodels()) {
                String submodelId = submodel.getId();
                if (submodel.getSubmodelElements() != null) {
                    for (SubmodelElement element : submodel.getSubmodelElements()) {
                        // File 타입인지 확인
                        if (element instanceof org.eclipse.digitaltwin.aas4j.v3.model.File) {
                            org.eclipse.digitaltwin.aas4j.v3.model.File fileElement = (org.eclipse.digitaltwin.aas4j.v3.model.File) element;
                            String idShort = fileElement.getIdShort();

                            // 복합키 (aasId, submodelId, idShort)를 이용하여 DB에서 파일 메타 조회
                            FilesMeta meta = uploadMapper.selectFileMetaByKey(aasId, submodelId, idShort);
                            if (meta != null) {
                                metas.add(meta);
                            } else {
                                log.warn("DB에서 해당 복합키 (aasId: {}, submodelId: {}, idShort: {}) 의 파일 메타를 찾지 못함.",
                                        aasId, submodelId, idShort);
                            }
                        }
                    }
                }
            }
        }

        log.info("패키지 파일 '{}' (AAS ID {}) 에 해당하는 첨부파일 메타 개수: {}", packageFileName, aasId, metas.size());
        return metas;
    }

}
