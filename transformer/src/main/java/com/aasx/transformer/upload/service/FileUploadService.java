package com.aasx.transformer.upload.service;

import org.apache.poi.openxml4j.exceptions.InvalidFormatException;
import org.apache.poi.openxml4j.opc.OPCPackage;
import org.eclipse.digitaltwin.aas4j.v3.dataformat.aasx.InMemoryFile;
import org.eclipse.digitaltwin.aas4j.v3.dataformat.core.internal.visitor.AssetAdministrationShellElementWalkerVisitor;
import org.eclipse.digitaltwin.aas4j.v3.model.Environment;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.aasx.transformer.deserializer.AASXFileDeserializer;
import com.aasx.transformer.deserializer.SHA256HashApache;

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

        for (int i = 0; i < uploadedFileNames.size(); i++) {
            String fileName = uploadedFileNames.get(i);
            Environment environment = uploadedEnvironments.get(i);
            List<String> paths = aasxFileDeserializer.parseReferencedFilePathsFromAASX(environment);

            if (paths.isEmpty()) {
                inMemoryFilesMap.put(fileName, Collections.emptyList());
                continue;
            }

            String aasxFilePath = uploadPath + File.separator + fileName;
            File aasxFile = new File(aasxFilePath);

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
    public Map<String, Environment> computeSHA256HashesForInMemoryFiles() {
        Map<String, Environment> updatedEnvironmentMap = new HashMap<>();
        Map<String, List<InMemoryFile>> inMemoryFilesMap = getInMemoryFilesFromReferencedPaths();

        for (Map.Entry<String, List<InMemoryFile>> entry : inMemoryFilesMap.entrySet()) {
            String fileName = entry.getKey();
            List<InMemoryFile> inMemoryFiles = entry.getValue();

            int index = uploadedFileNames.indexOf(fileName);
            if (index < 0 || index >= uploadedEnvironments.size()) {
                log.warn("해당 파일 이름에 대응하는 환경을 찾을 수 없음: {}", fileName);
                continue;
            }
            Environment environment = uploadedEnvironments.get(index);

            if (inMemoryFiles.isEmpty()) {
                log.info("첨부파일이 없습니다. Environment의 File 객체를 업데이트하지 않습니다: {}", fileName);
            } else {
                for (InMemoryFile inMemoryFile : inMemoryFiles) {
                    try {
                        String hash = SHA256HashApache.computeSHA256Hash(inMemoryFile);
                        String extension = "";
                        String originalPath = inMemoryFile.getPath();
                        if (originalPath != null && originalPath.contains(".")) {
                            extension = originalPath.substring(originalPath.lastIndexOf("."));
                        }
                        String newFileName = hash + extension;
                        String newFilePath = uploadPath + File.separator + newFileName;
                        File newFile = new File(newFilePath);

                        if (!newFile.exists()) {
                            try (FileOutputStream fos = new FileOutputStream(newFile)) {
                                fos.write(inMemoryFile.getFileContent());
                            }
                            log.info("첨부파일 저장: 해시값 {} 기반 파일명 {} 으로 저장됨", hash, newFileName);
                        } else {
                            log.info("중복 파일 발견: {} (이미 저장됨)", newFileName);
                        }

                        // 첨부파일 다운로드 전용 URL 생성
                        String hashBasedUrl = baseDownloadUrl + "/api/transformer/download/" + hash + extension;
                        log.info("생성된 해시 기반 URL: {}", hashBasedUrl);

                        // 원본 첨부파일 경로(originalPath)와 새 URL(hashBasedUrl)를 전달하여 해당 File 객체만 업데이트
                        updateEnvironmentFilePathsToURL(environment, originalPath, hashBasedUrl);

                    } catch (Exception e) {
                        log.error("해시 계산 중 오류 발생: {}", e.getMessage(), e);
                    }
                }
            }
            updatedEnvironmentMap.put(fileName, environment);
        }

        // 추가: 생성된 updatedEnvironmentMap의 키 목록 로그 출력
        log.info("업데이트된 Environment Map의 파일 이름 목록: {}", updatedEnvironmentMap.keySet());

        return updatedEnvironmentMap;
    }

    // ✅ Environment 내 File 객체의 value 값을 원본 경로와 일치하는 경우에만 해시 기반 URL로 변경
    public void updateEnvironmentFilePathsToURL(Environment environment, String originalPath, String updatedUrl) {
        AssetAdministrationShellElementWalkerVisitor visitor = new AssetAdministrationShellElementWalkerVisitor() {
            @Override
            public void visit(org.eclipse.digitaltwin.aas4j.v3.model.File file) {
                // file.getValue()가 원본 경로와 동일할 경우에만 업데이트 수행
                if (file != null && file.getValue() != null && file.getValue().equals(originalPath)) {
                    file.setValue(updatedUrl);
                    log.info("Updated file path from {} to {}", originalPath, updatedUrl);
                }
            }
        };
        visitor.visit(environment);
    }

}
