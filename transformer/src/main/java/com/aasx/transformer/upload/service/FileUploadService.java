package com.aasx.transformer.upload.service;

import org.apache.poi.openxml4j.exceptions.InvalidFormatException;
import org.apache.poi.openxml4j.opc.OPCPackage;
import org.eclipse.digitaltwin.aas4j.v3.dataformat.aasx.InMemoryFile;
import org.eclipse.digitaltwin.aas4j.v3.dataformat.core.internal.visitor.AssetAdministrationShellElementWalkerVisitor;
import org.eclipse.digitaltwin.aas4j.v3.model.Environment;
import org.eclipse.digitaltwin.aas4j.v3.model.Submodel;
import org.eclipse.digitaltwin.aas4j.v3.model.SubmodelElement;
import org.eclipse.digitaltwin.aas4j.v3.model.SubmodelElementCollection;
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
import java.util.concurrent.ConcurrentHashMap;
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
    public final List<String> uploadedFileNames = new CopyOnWriteArrayList<>();
    // 업로드된 AASX 파일로부터 변환된 Environment 목록
    private final List<Environment> uploadedEnvironments = new CopyOnWriteArrayList<>();

    public List<String> getUploadedFileNames() {
        return new ArrayList<>(uploadedFileNames);
    }

    public List<Environment> getUploadedEnvironments() {
        return new ArrayList<>(uploadedEnvironments);
    }

    // InMemoryFile의 원본 경로와 해시 매핑 (중복 체크, DB 등록 시 사용)
    // private final Map<String, String> pathToHashMap = new ConcurrentHashMap<>();

    // ✅ AASX 파일 업로드 및 Environment 변환
    // 파일 확장자 체크, 파일 저장, deserializer 수행
    public List<Environment> uploadFiles(MultipartFile[] files) {
        List<Environment> results = new ArrayList<>();
        uploadedFileNames.clear();
        uploadedEnvironments.clear();

        for (MultipartFile file : files) {

            String fileName = file.getOriginalFilename();

            log.info("업로드된 파일 이름: {}", fileName);
            log.info("업로드된 파일 크기: {}", file.getSize());

            try {
                // .aasx 파일만 처리
                if (fileName == null || !fileName.toLowerCase().endsWith(".aasx")) {
                    throw new IllegalArgumentException("AASX 파일(.aasx)만 업로드 가능합니다: " + fileName);
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
            // 1) AASX에서 참조된 파일 경로들 추출
            List<String> paths = aasxFileDeserializer.parseReferencedFilePathsFromAASX(environment);

            // 2) 절대 URI(외부 URL)는 건너뛰기
            paths.removeIf(p -> p.startsWith("http://") || p.startsWith("https://"));

            // 3) 남은 상대 경로가 없으면 빈 리스트로 매핑
            if (paths.isEmpty()) {
                inMemoryFilesMap.put(fileName, Collections.emptyList());
                continue;
            }

            // 4) OPCPackage로 AASX 내부 리소스 읽기
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

    /**
     * ✅ 동일 파일 검색 및 해시값 기반 파일 저장 후, fileName과 업데이트된 Environment를 매핑하여 반환
     * - 파일 해시 계산 후 DB 반영
     * - 물리 파일 저장
     * - Environment 내 File 요소의 경로를 다운로드 URL로 변경
     */
    public Map<String, Environment> computeSHA256HashesForInMemoryFiles() {
        log.info("computeSHA256HashesForInMemoryFiles 시작");

        Map<String, Environment> updatedEnvironmentMap = new HashMap<>();
        Map<String, List<InMemoryFile>> inMemoryFilesMap = getInMemoryFilesFromReferencedPaths();

        for (Map.Entry<String, List<InMemoryFile>> entry : inMemoryFilesMap.entrySet()) {
            String fileNameKey = entry.getKey();
            List<InMemoryFile> inMemoryFiles = entry.getValue();

            // Environment 존재 확인
            int index = uploadedFileNames.indexOf(fileNameKey);
            if (index < 0 || index >= uploadedEnvironments.size()) {
                log.warn("해당 파일 이름에 대응하는 Environment가 없습니다: {}", fileNameKey);
                continue;
            }

            // 첨부파일 존재 확인
            Environment environment = uploadedEnvironments.get(index);
            if (inMemoryFiles.isEmpty()) {
                log.info("첨부파일이 없습니다: {}", fileNameKey);
                updatedEnvironmentMap.put(fileNameKey, environment);
                continue;
            }

            for (InMemoryFile inMemoryFile : inMemoryFiles) {
                try {
                    String originalPath = inMemoryFile.getPath();
                    log.info("InMemoryFile 처리 시작, 원본 경로: {}", originalPath);

                    // 1) SHA-256 해시 계산 및 파일 크기 구하기
                    String hash = SHA256HashApache.computeSHA256Hash(inMemoryFile);
                    int fileSize = inMemoryFile.getFileContent().length;

                    // 2) DB의 files 테이블에 파일 해시 등록 (hash, size)
                    // pathToHashMap.put(originalPath, hash);
                    uploadMapper.insertFile(hash, fileSize);
                    // 파일 테이블에 잘 들어갔는지 확인용
                    Files fileInfo = uploadMapper.selectFileByHash(hash);
                    if (fileInfo != null) {
                        log.info("Files 등록 확인: hash={}, ref_count={}, size={}", fileInfo.getHash(),
                                fileInfo.getRefCount(), fileInfo.getSize());
                    } else {
                        log.warn("Files 등록 실패: hash={}", hash);
                    }

                    // 3) deriveCompositeKeyFromEnvironmentFull : Environment 전체를 순회해서 원본 경로와 일치하는
                    // File 객체의 composite key 도출
                    // Environment 객체를 통해 해당 파일의 aas_id, submodel_id, idShort 추출
                    // Environment 기반으로 composite key를 얻음 (형식: "aasId::submodelId::idShort")
                    String compositeKey = deriveCompositeKeyFromEnvironmentFull(environment, originalPath);
                    String[] keys = compositeKey.split("::");
                    String aasId = keys.length > 0 ? keys[0] : "unknown";
                    String submodelId = keys.length > 1 && keys[1] != null && !keys[1].trim().isEmpty() ? keys[1]
                            : "fallbackSubmodel";
                    String idShort = keys.length > 2 && keys[2] != null && !keys[2].trim().isEmpty() ? keys[2]
                            : new File(originalPath).getName();
                    if ("fallbackSubmodel".equals(submodelId) || "fallbackSubmodel".equals(idShort)) {
                        log.warn("Fallback composite key 적용: aasId={}, submodelId={}, idShort={}", aasId, submodelId,
                                idShort);
                    }

                    // 4) Environment 내에서 원본 파일의 상대경로에서 파일명과 확장자 추출
                    // 예: example.jpg
                    String baseName = new File(originalPath).getName();
                    String extractedName;
                    String extension = "";
                    int dotIndex = baseName.lastIndexOf(".");
                    if (dotIndex > 0) {
                        // extractedName : 인덱스 0부터 점이 시작되기 전까지의 문자열을 추출
                        extractedName = baseName.substring(0, dotIndex);
                        // extension : 점부터 끝까지의 문자열을 추출
                        extension = baseName.substring(dotIndex);
                    } else {
                        // 만약 점이 없으면, 파일 이름 전체를 extractedName으로 처리하고, extension은 빈 문자열로 유지
                        extractedName = baseName;
                    }
                    log.info("추출 결과: name={}, extension={}", extractedName, extension);

                    // 5) DB에서 파일 메타 조회. 없으면 새로 삽입
                    FilesMeta meta = uploadMapper.selectFileMetaByPath(aasId, submodelId, idShort);
                    if (meta == null) {
                        log.warn("DB에서 복합키 (aasId={}, submodelId={}, idShort={}) 메타 미존재", aasId, submodelId, idShort);
                        log.info("파일 요소 처리: idShort={}, 원본 경로='{}'", idShort, originalPath);
                        if (originalPath == null || originalPath.trim().isEmpty()) {
                            log.warn("원본 경로가 비어있어 처리 건너뜀: idShort={}", idShort);
                            continue;
                        }

                        // files_meta 테이블에 파일 메타 등록
                        FilesMeta newMeta = new FilesMeta();
                        newMeta.setAasId(aasId);
                        newMeta.setSubmodelId(submodelId);
                        newMeta.setIdShort(idShort);
                        newMeta.setName(extractedName);
                        newMeta.setExtension(extension);
                        String contentType = retrieveContentType(environment, originalPath);
                        newMeta.setContentType(contentType);
                        newMeta.setHash(hash);
                        log.info("새 파일 메타 삽입: aasId={}, submodelId={}, idShort={}, name={}", aasId, submodelId, idShort,
                                extractedName);
                        uploadMapper.insertFileMeta(newMeta);
                        // 신규 삽입 후 ref_count 재계산
                        uploadMapper.updateFileRefCount(hash);
                        // 파일메타 테이블에 잘 들어갔는지 확인용
                        meta = uploadMapper.selectFileMetaByPath(aasId, submodelId, idShort);
                        if (meta == null) {
                            log.warn("파일 메타 삽입 후 재조회 실패: aasId={}, submodelId={}, idShort={}", aasId, submodelId,
                                    idShort);
                            continue;
                        }
                    } else {
                        log.info("이미 파일 메타 존재: {}", meta);
                    }

                    // 6) 물리 파일 저장 (이미 존재하면 저장하지 않음)
                    String newFileName = hash + extension;
                    String newFilePath = uploadPath + File.separator + newFileName;
                    File newFile = new File(newFilePath);
                    if (!newFile.exists()) {
                        try (FileOutputStream fos = new FileOutputStream(newFile)) {
                            fos.write(inMemoryFile.getFileContent());
                        }
                        log.info("첨부파일 저장됨: {}", newFilePath);
                    } else {
                        log.info("첨부파일 이미 존재: {}", newFilePath);
                    }

                    // 7) 다운로드 URL 구성: baseDownloadUrl + "/api/transformer/download/" + hash +
                    // extension
                    String hashBasedUrl = baseDownloadUrl + "/api/transformer/download/" + hash + extension;
                    log.info("다운로드 URL 구성됨: {}", hashBasedUrl);

                    // 8) Environment 내 File 객체의 값이 originalPath와 일치하면 다운로드 URL로 업데이트
                    updateEnvironmentFilePathsToURL(environment, originalPath, hashBasedUrl);
                } catch (Exception e) {
                    log.error("해시 계산/DB 반영 중 오류: {}", e.getMessage(), e);
                }
            }
            updatedEnvironmentMap.put(fileNameKey, environment);
        }
        log.info("computeSHA256HashesForInMemoryFiles 종료, 업데이트된 파일 개수: {}", updatedEnvironmentMap.size());
        return updatedEnvironmentMap;
    }

    // ✅ 3) 복합 키 도출 메서드
    // - normalizePath(String path)
    // - findFileElementRecursive(List<SubmodelElement> elements, String
    // parentSubmodelId, String normalizedPath, String[] result)
    private String deriveCompositeKeyFromEnvironmentFull(Environment environment, String originalPath) {
        // 환경 내 AAS들이 여러 개 있을 수 있으므로 idShort를 기준으로 해당하는 AAS의 id를 찾아야 함
        String aasId = null;
    
        if (environment.getAssetAdministrationShells() == null
                || environment.getAssetAdministrationShells().isEmpty()) {
            throw new RuntimeException("Environment에 등록된 AAS가 없습니다.");
        }
    
        // AAS의 idShort에 해당하는 AAS를 찾기 위한 탐색
        for (org.eclipse.digitaltwin.aas4j.v3.model.AssetAdministrationShell aas : environment
                .getAssetAdministrationShells()) {
            // AAS의 idShort를 비교하여 해당하는 AAS를 찾음
            if (originalPath != null && !originalPath.trim().isEmpty()) {
                // 실제로 AAS에서 가져온 id를 사용하는 방식
                aasId = aas.getId();
                log.info("aasId : {} ", aasId);
                break;  // 일치하는 AAS를 찾으면 루프 종료
            }
        }
    
        // AAS ID가 없는 경우 기본 처리
        if (aasId == null) {
            log.info("해당 AAS가 없으므로 기본 AAS의 ID를 사용합니다.");
            aasId = environment.getAssetAdministrationShells().get(0).getId();
        }
    
        String normalizedOriginalPath = normalizePath(originalPath);
        // Submodel 및 File 정보(파일 요소의 idShort 등)를 위한 임시 배열
        String[] result = new String[] { "", "" };
    
        log.info("deriveCompositeKeyFromEnvironmentFull 시작: originalPath='{}', normalized='{}'",
                originalPath, normalizedOriginalPath);
    
        // 환경 내의 모든 Submodel을 순회하면서, 각 Submodel의 SubmodelElement 목록을 대상으로 재귀 탐색을 실시
        if (environment.getSubmodels() != null) {
            for (Submodel submodel : environment.getSubmodels()) {
                if (submodel.getSubmodelElements() == null)
                    continue;
                log.info("Submodel '{}' 내 검사 시작.", submodel.getId());
                // 재귀 탐색 메서드(findFileElementRecursive)를 호출하여 normalizedOriginalPath와 일치하는 File
                // 요소를 찾음
                if (findFileElementRecursive(submodel.getSubmodelElements(), submodel.getId(), normalizedOriginalPath,
                        result)) {
                    // result 배열에 [부모 Submodel ID, File의 idShort]가 저장
                    log.info("매칭 결과: parentSubmodelId='{}', idShort='{}'", result[0], result[1]);
                    break;
                }
            }
        }
    
        // 탐색 결과가 없어서 result 배열의 값이 비어있다면, fallback 처리
        if (result[0].isEmpty() || result[1].isEmpty()) {
            log.warn("deriveCompositeKeyFromEnvironmentFull: 매칭되는 File 요소를 찾지 못함 for normalized originalPath: '{}'",
                    normalizedOriginalPath);
            // fallback 처리: idShort는 파일명으로 대체, submodelId는 "fallbackSubmodel"으로 대체
            result[0] = "fallbackSubmodel";
            result[1] = new File(originalPath).getName();
        }
    
        // 최종적으로 "aasId::submodelId::idShort" 형식의 문자열을 생성하여 반환
        String compositeKey = aasId + "::" + result[0] + "::" + result[1];
        log.info("도출된 Composite Key: {}", compositeKey);
        return compositeKey;
    }
    
    /**
     * ✅ 경로를 정규화
     * - 백슬래시를 슬래시로 변경
     * - 앞뒤 공백 제거
     * - 소문자 변환하여 비교 오차 제거
     */
    private String normalizePath(String path) {
        if (path == null)
            return "";
        // 경로를 소문자 변환하기 전에, 슬래시 통일 및 앞뒤 공백 제거
        String replaced = path.replace("\\", "/").trim();
        return replaced.toLowerCase();
    }

    /**
     * ✅ 재귀적으로 SubmodelElement 리스트(혹은 SubmodelElementCollection 내부)를 순회하면서,
     * normalizedPath와 일치하는 File 요소를 찾으면 result 배열에
     * [parentSubmodelId, file.idShort]를 저장하고 true를 반환
     *
     * @param elements         현재 탐색할 SubmodelElement 목록
     * @param parentSubmodelId 탐색 중인 File 요소가 속한 상위 Submodel의 id
     * @param normalizedPath   비교할 정규화된 경로 문자열
     * @param result           결과를 저장할 배열, index 0: submodelId, index 1: file
     *                         idShort
     * @return 일치하는 File 요소를 찾았으면 true, 아니면 false
     */
    private boolean findFileElementRecursive(List<SubmodelElement> elements, String parentSubmodelId,
            String normalizedPath, String[] result) {
        if (elements == null)
            return false;

        // elements 리스트에 포함된 모든 요소를 순회
        for (SubmodelElement element : elements) {

            // 1️⃣ 요소가 File 타입인 경우 처리
            // 해당 요소가 File 요소인지 확인하여 fileElement로 캐스팅한 후 그 속성 중 하나인 value를 가져옴
            if (element instanceof org.eclipse.digitaltwin.aas4j.v3.model.File) {
                org.eclipse.digitaltwin.aas4j.v3.model.File fileElement = (org.eclipse.digitaltwin.aas4j.v3.model.File) element;
                String fileValue = fileElement.getValue();
                // 먼저 null 체크 및 trim
                // 해당 File 요소는 유효하지 않으므로 건너뜀
                if (fileValue == null || fileValue.trim().isEmpty()) {
                    log.info("빈 file value 건너뜀, idShort: {}", fileElement.getIdShort());
                    continue;
                }

                // 경로 비교 준비: Environment 내의 여러 Submodel에서 File 요소를 재귀적으로 탐색하여,
                // 정규화한 경로와 일치하는 File을 찾음
                String normalizedFileValue = normalizePath(fileValue);
                log.info("재귀 검사 - File 요소: idShort='{}', 원본='{}', normalized='{}', 비교대상='{}'",
                        fileElement.getIdShort(), fileValue, normalizedFileValue, normalizedPath);

                // 경로 비교 및 결과 저장
                // 정규화된 파일 value와 normalizedPath의 일치를 확인
                // 원본 value의 trim 처리 후 normalizedPath와 비교
                if (normalizedFileValue.equals(normalizedPath) || fileValue.trim().equals(normalizedPath)) {
                    // result 배열에 현재 전달된 parentSubmodelId를 저장(즉, File 요소가 속한 Submodel의 ID)
                    // 해당 File 요소의 idShort를 저장
                    result[0] = parentSubmodelId;
                    result[1] = fileElement.getIdShort();
                    log.info("재귀 탐색: 매칭된 File 요소 발견 - idShort='{}', parentSubmodelId='{}'", result[1], result[0]);
                    return true;
                }
            }

            // 2️⃣ 요소가 SubmodelElementCollection인 경우 처리
            // 여러 자식 요소(일반 File 요소나 또 다른 Collection 등)를 포함할 수 있는 컨테이너
            else if (element instanceof SubmodelElementCollection) {
                // collection.getValue() : SubmodelElementCollection의 자식 요소 탐색
                SubmodelElementCollection collection = (SubmodelElementCollection) element;
                List<SubmodelElement> innerElements = collection.getValue();
                // 만약 null이면 다시 한 번 체크 (getValue()가 두 번 호출되는 경우 대비)
                if (innerElements == null && collection.getValue() != null) {
                    innerElements = collection.getValue();
                }
                // 내부 요소들이 존재하면 재귀적으로 findFileElementRecursive를 호출하여 해당 내부 요소들을 검사
                if (innerElements != null) {
                    log.info("재귀 검사 - SubmodelElementCollection 발견, 상위 Submodel: '{}'", parentSubmodelId);
                    if (findFileElementRecursive(innerElements, parentSubmodelId, normalizedPath, result)) {
                        return true;
                    }
                }
            }

            // 3️⃣ 그 밖의 다른 컨테이너 타입 처리
            // 다른 컨테이너 타입: reflection으로 getValue() 메서드가 있는지 확인
            // - Reflection
            // : Java에서 클래스의 메타데이터(예: 클래스 이름, 메서드, 필드 등)를 런타임에 동적으로 조회하고 조작할 수 있게 해주는 기능
            // Reflection을 사용하여 해당 정보를 런타임에 동적으로 가져오거나, 메서드를 호출하거나, 필드 값을 읽고 수정 가능
            // - getValue() 메서드
            // : 보통 해당 객체가 보유한 데이터나 자식 요소 리스트(예, 컬렉션)를 반환하기 위해 사용
            // 예를 들어, SubmodelElementCollection 객체에는 여러 하위 SubmodelElement들이 포함되어 있을 수 있음
            // 이 경우 getValue() 메서드를 호출하면 그 하위 요소들의 리스트를 반환
            else {
                try {
                    // 직접 getValue() 메서드가 정의되어 있는지 reflection을 사용하여 확인
                    java.lang.reflect.Method getValueMethod = element.getClass().getMethod("getValue");
                    if (getValueMethod != null
                            && List.class.isAssignableFrom(getValueMethod.getReturnType())) {
                        @SuppressWarnings("unchecked")
                        List<SubmodelElement> childElements = (List<SubmodelElement>) getValueMethod.invoke(element);
                        // getValue() 메서드가 존재하고, 반환 타입이 List이면, 해당 메서드를 호출하여 자식 요소를 가져
                        if (childElements != null && !childElements.isEmpty()) {
                            log.info("재귀 검사 - {} 타입의 컨테이너 발견, 상위 Submodel: '{}'", element.getClass().getSimpleName(),
                                    parentSubmodelId);
                            // 자식 요소 리스트가 존재하면, 재귀적 으로 동일한 탐색 메서드를 호출
                            if (findFileElementRecursive(childElements, parentSubmodelId, normalizedPath, result)) {
                                return true;
                            }
                        }
                    }
                } catch (NoSuchMethodException nsme) {
                    // getValue가 없으면 다른 컨테이너가 아니므로 무시
                } catch (Exception e) {
                    log.warn("자식 요소 탐색 중 예외 발생: {}", e.getMessage());
                }
            }
        }
        // for 문 전체를 순회한 후에도 조건을 만족하는 File 요소를 찾지 못하면 false를 반환
        return false;
    }

    // ✅ 5) Content-Type 조회
    private String retrieveContentType(Environment env, String originalPath) {
        final String[] result = new String[] { "application/octet-stream" }; // default value
        AssetAdministrationShellElementWalkerVisitor visitor = new AssetAdministrationShellElementWalkerVisitor() {
            @Override
            public void visit(org.eclipse.digitaltwin.aas4j.v3.model.File file) {
                if (file == null) {
                    return;
                }
                String fileValue = file.getValue();
                if (fileValue != null && fileValue.trim().equalsIgnoreCase(originalPath.trim())) {
                    if (file.getContentType() != null && !file.getContentType().trim().isEmpty()) {
                        result[0] = file.getContentType();
                        log.info("탐색된 contentType: {}", result[0]);
                    }
                }
            }
        };
        visitor.visit(env);
        return result[0];
    }

    // ✅ 8) Environment 내 File 객체의 value 값을 원본 경로와 일치하는 경우에만 해시 기반 URL로 변경
    public void updateEnvironmentFilePathsToURL(Environment environment, String originalPath, String hashBaseUrl) {
        AssetAdministrationShellElementWalkerVisitor visitor = new AssetAdministrationShellElementWalkerVisitor() {

            @Override
            public void visit(org.eclipse.digitaltwin.aas4j.v3.model.File file) {
                if (file == null) {
                    return;
                }
                String fileValue = file.getValue();
                // value가 채워진 File 요소만 처리
                if (fileValue != null && !fileValue.trim().isEmpty()) {
                    log.info("현재 file value: {}", fileValue);
                    if (fileValue.trim().equals(originalPath.trim())) {
                        file.setValue(hashBaseUrl);
                        log.info("Updated file path from {} to {}", originalPath, hashBaseUrl);
                    } else {
                        log.info("파일 value와 원본 경로 불일치 - file value: [{}], originalPath: [{}]", fileValue, originalPath);
                    }
                } else {
                    // 빈 value를 가진 File 요소는 건너뜁니다.
                    log.info("빈 value를 가진 File 요소 건너뜀, idShort: {}", file.getIdShort());
                }
            }
        };
        visitor.visit(environment);
    }

    /**
     * ✅ 파일 메타 삭제 (클라이언트 요청 시)
     * DB에서 파일 메타를 삭제한 후 해당 해시의 ref_count를 감소시키고, 0이면 files 테이블에서도 삭제
     */
    public void deleteFileMeta(String compositeKey) {
        String[] keys = compositeKey.split("::");
        if (keys.length < 3) {
            log.warn("파일 메타 삭제를 위한 key 형식이 올바르지 않음: {}", compositeKey);
            return;
        }
        String aasId = keys[0];
        String submodelId = keys[1];
        String idShort = keys[2];

        // 1) 메타 조회
        FilesMeta meta = uploadMapper.selectFileMetaByPath(aasId, submodelId, idShort);
        if (meta == null) {
            log.warn("파일 메타가 존재하지 않음: {}", compositeKey);
            return;
        }

        String hash = meta.getHash();
        String extension = meta.getExtension();

        // 2) 메타 삭제 및 ref_count 감소
        uploadMapper.deleteFileMeta(aasId, submodelId, idShort);
        log.info("파일 메타 삭제됨: {}", compositeKey);
        uploadMapper.updateFileRefCount(hash);

        // 3) files 테이블에서 row 제거 조건 검사
        Files fileInfo = uploadMapper.selectFileByHash(hash);
        if (fileInfo == null || fileInfo.getRefCount() <= 0) {
            uploadMapper.deleteFileByHash(hash);
            log.info("ref_count 0으로 인해 files 테이블에서도 삭제됨: {}", hash);

            // 4) 물리 디스크 파일 삭제
            File physical = new File(uploadPath + File.separator + hash + extension);
            if (physical.exists()) {
                if (physical.delete()) {
                    log.info("물리 첨부파일 삭제됨: {}", physical.getAbsolutePath());
                } else {
                    log.warn("물리 첨부파일 삭제 실패: {}", physical.getAbsolutePath());
                }
            } else {
                log.warn("삭제할 물리 첨부파일이 없음: {}", physical.getAbsolutePath());
            }
        }
    }
}
