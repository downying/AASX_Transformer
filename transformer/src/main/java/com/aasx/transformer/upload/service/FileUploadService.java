package com.aasx.transformer.upload.service;

import org.apache.poi.openxml4j.exceptions.InvalidFormatException;
import org.apache.poi.openxml4j.opc.OPCPackage;
import org.eclipse.digitaltwin.aas4j.v3.dataformat.aasx.InMemoryFile;
import org.eclipse.digitaltwin.aas4j.v3.dataformat.core.internal.visitor.AssetAdministrationShellElementWalkerVisitor;
import org.eclipse.digitaltwin.aas4j.v3.model.AssetAdministrationShell;
import org.eclipse.digitaltwin.aas4j.v3.model.AssetInformation;
import org.eclipse.digitaltwin.aas4j.v3.model.Environment;
import org.eclipse.digitaltwin.aas4j.v3.model.Resource;
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
import java.lang.reflect.Method;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

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

    // ✅ ★ helper 1) InMemoryFile 객체 목록을 반환 (환경 내 참조된 파일 경로 처리)
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
     * ✅ 동일 파일 해시 계산 및 저장 → URL 매핑까지
     * 
     * 1) LinkedHashMap 으로 결과 순서 유지
     * 2) 각 경로별 compositeKey 큐를 미리 구성하여, 동일 경로 여러 파일 처리 시 중복 키 분배
     * 3) InMemoryFile마다
     * - 해시 계산 및 DB 등록
     * - FilesMeta 조회/삽입 및 ref count 갱신
     * - 물리 파일 저장
     * - 다운로드 URL 생성
     * 4) 최종적으로 urlMap에 저장된 (원본경로→URL) 매핑을 Environment 내 File 요소에 적용
     */
    public Map<String, Environment> computeSHA256HashesForInMemoryFiles() {
        log.info("computeSHA256HashesForInMemoryFiles 시작");

        // 순서 보존이 필요하므로 LinkedHashMap 사용
        Map<String, Environment> updatedEnvironmentMap = new LinkedHashMap<>();
        // 1) AASX 내부 참조 파일 가져오기
        Map<String, List<InMemoryFile>> inMemoryFilesMap = getInMemoryFilesFromReferencedPaths();

        for (Map.Entry<String, List<InMemoryFile>> entry : inMemoryFilesMap.entrySet()) {
            String fileNameKey = entry.getKey(); // AASX 파일 이름
            List<InMemoryFile> inMemoryFiles = entry.getValue(); // 해당 AASX의 InMemoryFile 목록

            int index = uploadedFileNames.indexOf(fileNameKey);
            if (index < 0)
                continue; // 환경 매핑이 없으면 건너뜀

            Environment environment = uploadedEnvironments.get(index);
            if (inMemoryFiles.isEmpty()) {
                // 첨부파일 없으면 기존 Environment 그대로 반환
                updatedEnvironmentMap.put(fileNameKey, environment);
                continue;
            }

            // --- 복합키 큐 구성 ---
            // 2) 모든 InMemoryFile의 정규화된 경로 집합 생성 → 중복 제거 및 순서 보존
            Set<String> normalizedPaths = new LinkedHashSet<>();
            for (InMemoryFile mem : inMemoryFiles) {
                normalizedPaths.add(normalizePath(mem.getPath()));
            }

            // 정규화 경로별로 가능한 모든 compositeKey를 미리 수집하여 Deque로 저장
            Map<String, Deque<String>> compositeQueues = new HashMap<>();
            for (String norm : normalizedPaths) {
                List<String> keys = collectCompositeKeys(environment, norm);
                compositeQueues.put(norm, new ArrayDeque<>(keys));
            }

            // --- 파일별 처리 ---
            Map<String, String> urlMap = new LinkedHashMap<>(); // (원본경로→생성 URL) 매핑
            for (InMemoryFile inMemoryFile : inMemoryFiles) {
                String originalPath = inMemoryFile.getPath();
                String norm = normalizePath(originalPath);
                Deque<String> queue = compositeQueues.get(norm);

                // 큐에서 사용 가능한 compositeKey를 꺼내고, 없으면 fallback 메서드 호출
                String compositeKey = (queue != null && !queue.isEmpty())
                        ? queue.pollFirst()
                        : deriveCompositeKeyFromEnvironmentFull(environment, originalPath);

                String[] parts = compositeKey.split("::");
                String aasId = parts[0];
                String submodelId = parts[1];
                String idShort = parts[2];

                try {
                    // 1) SHA-256 해시 계산 및 DB files 테이블 등록
                    String hash = SHA256HashApache.computeSHA256Hash(inMemoryFile);
                    int fileSize = inMemoryFile.getFileContent().length;
                    uploadMapper.insertFile(hash, fileSize);

                    // 2) FilesMeta 조회, 없으면 새로 삽입 및 ref_count 갱신
                    FilesMeta meta = uploadMapper.selectFileMetaByPath(aasId, submodelId, idShort);
                    if (meta == null) {
                        FilesMeta newMeta = new FilesMeta();
                        newMeta.setAasId(aasId);
                        newMeta.setSubmodelId(submodelId);
                        newMeta.setIdShort(idShort);

                        String baseName = new File(originalPath).getName();
                        int dotIndex = baseName.lastIndexOf('.');
                        newMeta.setName(dotIndex > 0 ? baseName.substring(0, dotIndex) : baseName);
                        newMeta.setExtension(dotIndex > 0 ? baseName.substring(dotIndex) : "");

                        // Content-Type: default thumbnail 여부까지 포함한 단일 메서드 호출
                        newMeta.setContentType(retrieveContentType(environment, originalPath));

                        // ★ 파일 메타에 'path' 컬럼으로 AASX 내부 상대경로(value) 저장
                        newMeta.setPath(originalPath);
                        log.info("FilesMeta.path 으로 저장될 상대경로: {}", originalPath);

                        newMeta.setHash(hash);
                        uploadMapper.insertFileMeta(newMeta);
                        uploadMapper.updateFileRefCount(hash);

                        meta = uploadMapper.selectFileMetaByPath(aasId, submodelId, idShort);
                    }

                    // 3) 물리 파일로 저장 (중복 저장 방지)
                    File newFile = new File(uploadPath + File.separator + hash + meta.getExtension());
                    if (!newFile.exists()) {
                        try (FileOutputStream fos = new FileOutputStream(newFile)) {
                            fos.write(inMemoryFile.getFileContent());
                        }
                        log.info("첨부파일 저장됨: {}", newFile.getAbsolutePath());
                    }

                    // 4) 다운로드 URL 생성 및 매핑
                    String url = baseDownloadUrl + "/api/transformer/download/" + hash + meta.getExtension();
                    urlMap.put(originalPath, url);

                } catch (Exception e) {
                    log.error("처리 실패 ({}): {}", originalPath, e.getMessage(), e);
                }
            }

            // --- 환경 내 File 요소 경로 업데이트 ---
            urlMap.forEach((orig, url) -> updateEnvironmentFilePathsToURL(environment, orig, url));
            updatedEnvironmentMap.put(fileNameKey, environment);
        }

        log.info("computeSHA256HashesForInMemoryFiles 종료, 업데이트된 파일 개수: {}", updatedEnvironmentMap.size());
        return updatedEnvironmentMap;
    }

    /**
     * ✅ ★ helper 2) 주어진 정규화된 경로(normalizedPath)에 매칭되는 모든 compositeKey를 수집하여 반환
     *
     * @param environment    AASX에서 변환된 Environment 객체
     * @param normalizedPath 파일 경로를 정규화한 문자열 (소문자, 슬래시 통일)
     * @return List of "aasId::submodelId::idShort" 형태의 Keys
     */
    private List<String> collectCompositeKeys(Environment environment, String normalizedPath) {
        List<String> composites = new ArrayList<>();

        // 0) AAS 목록 검사
        if (environment.getAssetAdministrationShells() == null
                || environment.getAssetAdministrationShells().isEmpty()) {
            throw new RuntimeException("Environment에 등록된 AAS가 없습니다.");
        }

        // 1) Asset default thumbnail 처리 (Reflection 사용)
        for (org.eclipse.digitaltwin.aas4j.v3.model.AssetAdministrationShell shell : environment
                .getAssetAdministrationShells()) {
            Object assetInfo = shell.getAssetInformation();
            if (assetInfo == null)
                continue;

            try {
                // defaultThumbnail 얻기
                Method mThumb = assetInfo.getClass().getMethod("getDefaultThumbnail");
                Object thumb = mThumb.invoke(assetInfo);
                if (thumb == null)
                    continue;

                String thumbVal = null;

                // → getValue(), getValueUrl(), getPath() 순으로 시도하되
                // 캐스팅 오류도 같이 잡아서 다음으로 넘어가도록
                try {
                    Method mVal = thumb.getClass().getMethod("getValue");
                    Object raw = mVal.invoke(thumb);
                    if (raw instanceof String) {
                        thumbVal = (String) raw;
                    } else {
                        throw new ClassCastException("getValue() 반환 타입 불일치: " + raw.getClass());
                    }
                } catch (NoSuchMethodException | ClassCastException e1) {
                    try {
                        Method mVal2 = thumb.getClass().getMethod("getValueUrl");
                        Object raw2 = mVal2.invoke(thumb);
                        if (raw2 instanceof String) {
                            thumbVal = (String) raw2;
                        } else {
                            throw new ClassCastException("getValueUrl() 반환 타입 불일치: " + raw2.getClass());
                        }
                    } catch (NoSuchMethodException | ClassCastException e2) {
                        try {
                            Method mVal3 = thumb.getClass().getMethod("getPath");
                            Object raw3 = mVal3.invoke(thumb);
                            if (raw3 instanceof String) {
                                thumbVal = (String) raw3;
                            } else {
                                throw new ClassCastException("getPath() 반환 타입 불일치: " + raw3.getClass());
                            }
                        } catch (NoSuchMethodException | ClassCastException ignored) {
                            // 셋 다 실패하면 thumbVal은 null
                        }
                    }
                }

                if (thumbVal == null || !normalizePath(thumbVal).equals(normalizedPath)) {
                    continue;
                }

                // 일치할 때만 compositeKey 생성
                Method mGlobal = assetInfo.getClass().getMethod("getGlobalAssetId");
                String globalAssetId = (String) mGlobal.invoke(assetInfo);
                String idShort = new File(thumbVal).getName();
                String aasId = shell.getId();
                composites.add(aasId + "::" + globalAssetId + "::" + idShort);
                return composites;

            } catch (Exception e) {
                log.warn("default thumbnail reflection 처리 중 예외: {}", e.toString());
            }
        }

        // 2) Asset default thumbnail 이 아닐 때, 기존 AAS ID 탐색 로직
        String aasId = null;
        for (AssetAdministrationShell shell : environment.getAssetAdministrationShells()) {
            aasId = shell.getId();
            log.info("aasId : {}", aasId);
            break;
        }
        if (aasId == null) {
            aasId = environment.getAssetAdministrationShells().get(0).getId();
            log.info("기본 AAS ID 사용: {}", aasId);
        }

        // 3) Submodel 순회하여 File 요소 찾아 compositeKey 생성
        for (Submodel submodel : environment.getSubmodels()) {
            List<String> ids = new ArrayList<>();
            findFileElementIdsRecursive(
                    submodel.getSubmodelElements(),
                    submodel.getId(),
                    normalizedPath,
                    ids);
            for (String id : ids) {
                composites.add(aasId + "::" + submodel.getId() + "::" + id);
            }
        }

        return composites;
    }

    /**
     * ✅ 재귀적으로 SubmodelElement 목록을 순회하며, File 요소의 경로가 normalizedPath와 일치하면
     * idShort를
     * outIds에 추가
     *
     * @param elements         SubmodelElement 리스트 (File, Collection, 기타 컨테이너 포함)
     * @param parentSubmodelId 현재 탐색 중인 Submodel의 ID
     * @param normalizedPath   비교 대상 경로 (정규화됨)
     * @param outIds           일치하는 File 요소의 idShort를 수집할 리스트
     */
    @SuppressWarnings("unchecked")
    private void findFileElementIdsRecursive(List<SubmodelElement> elements,
            String parentSubmodelId,
            String normalizedPath,
            List<String> outIds) {
        if (elements == null)
            return;
        for (SubmodelElement element : elements) {
            // 1) File 요소인 경우 value 정규화 비교
            if (element instanceof org.eclipse.digitaltwin.aas4j.v3.model.File) {
                org.eclipse.digitaltwin.aas4j.v3.model.File fileEl = (org.eclipse.digitaltwin.aas4j.v3.model.File) element;
                String val = fileEl.getValue();
                if (val != null && normalizePath(val).equals(normalizedPath)) {
                    outIds.add(fileEl.getIdShort()); // 매칭된 idShort 추가
                }

                // 2) SubmodelElementCollection인 경우 내부 요소 재귀 탐색
            } else if (element instanceof SubmodelElementCollection) {
                findFileElementIdsRecursive(((SubmodelElementCollection) element).getValue(),
                        parentSubmodelId, normalizedPath, outIds);
                // 3) 기타 컨테이너 타입: reflection으로 getValue() 호출
            } else {
                try {
                    Method m = element.getClass().getMethod("getValue");
                    if (List.class.isAssignableFrom(m.getReturnType())) {
                        List<SubmodelElement> children = (List<SubmodelElement>) m.invoke(element);
                        findFileElementIdsRecursive(children, parentSubmodelId, normalizedPath, outIds);
                    }
                } catch (Exception e) {
                    log.warn("자식 탐색 실패: {}", e.getMessage());
                }
            }
        }
    }

    // ✅ ★ helper 3) 복합 키 도출 메서드
    // - normalizePath(String path)
    // - findFileElementRecursive(List<SubmodelElement> elements, String
    // parentSubmodelId, String normalizedPath, String[] result)
    public String deriveCompositeKeyFromEnvironmentFull(Environment environment, String originalPath) {
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
                break; // 일치하는 AAS를 찾으면 루프 종료
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
     * ✅ ★ helper 4) 경로를 정규화
     * - 백슬래시를 슬래시로 변경
     * - 앞뒤 공백 제거
     * - 소문자 변환하여 비교 오차 제거
     */
    public String normalizePath(String path) {
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
    public boolean findFileElementRecursive(List<SubmodelElement> elements, String parentSubmodelId,
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
                List<?> rawElements = collection.getValue();
                List<SubmodelElement> innerElements = rawElements.stream()
                        .filter(e -> e instanceof SubmodelElement)
                        .map(e -> (SubmodelElement) e)
                        .collect(Collectors.toList());
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

    /**
     * ✅ helper 5) Content-Type 조회
     * 주어진 파일 경로(originalPath)에 대한 Content-Type을 반환합니다.
     *
     * 1) AssetInformation.defaultThumbnail.path 와 일치하면 reflection 으로 contentType
     * 꺼내기
     * 2) 그 외엔 AAS 내 File 요소를 Visitor 로 순회하며 contentType 꺼내기
     */
    public String retrieveContentType(Environment env, String originalPath) {
        // 1) defaultThumbnail인지 먼저 검사
        try {
            AssetAdministrationShell shell = env.getAssetAdministrationShells().get(0);
            AssetInformation assetInfo = shell.getAssetInformation();
            if (assetInfo != null) {
                Resource thumb = assetInfo.getDefaultThumbnail();
                if (thumb != null) {
                    String thumbPath = thumb.getPath(); // Resource 인터페이스로 경로 획득
                    if (normalizePath(thumbPath).equals(normalizePath(originalPath))) {
                        String ct = thumb.getContentType(); // Resource 인터페이스로 contentType 획득
                        if (ct != null && !ct.isBlank()) {
                            log.info("default-thumbnail contentType: {}", ct);
                            return ct;
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.warn("defaultThumbnail 처리 중 예외: {}", e.toString());
        }

        // 2) defaultThumbnail 아닌 경우 AAS 내 File 요소 순회
        final String[] result = new String[] { "application/octet-stream" };
        new AssetAdministrationShellElementWalkerVisitor() {
            @Override
            public void visit(org.eclipse.digitaltwin.aas4j.v3.model.File file) {
                if (file == null)
                    return;
                String val = file.getValue();
                if (val != null
                        && normalizePath(val).equals(normalizePath(originalPath))
                        && file.getContentType() != null
                        && !file.getContentType().isBlank()) {
                    result[0] = file.getContentType();
                    log.info("탐색된 File 요소 contentType: {}", result[0]);
                }
            }
        }.visit(env);

        return result[0];
    }

    // ✅ ★ helper 6) Environment 내 File 객체의 value 값을 원본 경로와 일치하는 경우에만 해시 기반 URL로 변경
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
