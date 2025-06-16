package com.aasx.transformer.upload.service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import org.apache.commons.io.FilenameUtils;
import org.eclipse.digitaltwin.aas4j.v3.dataformat.aasx.AASXSerializer;
import org.eclipse.digitaltwin.aas4j.v3.dataformat.aasx.InMemoryFile;
import org.eclipse.digitaltwin.aas4j.v3.dataformat.core.DeserializationException;
import org.eclipse.digitaltwin.aas4j.v3.dataformat.core.internal.visitor.AssetAdministrationShellElementWalkerVisitor;
import org.eclipse.digitaltwin.aas4j.v3.dataformat.json.JsonDeserializer;
import org.eclipse.digitaltwin.aas4j.v3.dataformat.xml.XmlSerializer;
import org.eclipse.digitaltwin.aas4j.v3.model.AssetAdministrationShell;
import org.eclipse.digitaltwin.aas4j.v3.model.Environment;
import org.eclipse.digitaltwin.aas4j.v3.model.File;
import org.eclipse.digitaltwin.aas4j.v3.model.Key;
import org.eclipse.digitaltwin.aas4j.v3.model.KeyTypes;
import org.eclipse.digitaltwin.aas4j.v3.model.Reference;
import org.eclipse.digitaltwin.aas4j.v3.model.Resource;
import org.eclipse.digitaltwin.aas4j.v3.model.Submodel;
import org.eclipse.digitaltwin.aas4j.v3.model.SubmodelElement;
import org.eclipse.digitaltwin.aas4j.v3.model.SubmodelElementCollection;
import org.eclipse.digitaltwin.aas4j.v3.model.impl.DefaultKey;
import org.eclipse.digitaltwin.aas4j.v3.model.KeyTypes;
import org.eclipse.digitaltwin.aas4j.v3.model.Reference;
import org.eclipse.digitaltwin.aas4j.v3.model.SubmodelElement;
import org.eclipse.digitaltwin.aas4j.v3.dataformat.core.internal.visitor.AssetAdministrationShellElementWalkerVisitor;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.aasx.transformer.upload.dto.FilesMeta;
import com.aasx.transformer.upload.mapper.UploadMapper;

import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class JsonToAASXService {

    @Value("${upload.temp-path}")
    private String tempPath;

    @Value("${upload.path}")
    private String uploadPath;

    @Autowired
    private UploadMapper uploadMapper;

    @Autowired
    private FileUploadService fileUploadService;

    // JSON → Environment 파싱용 Deserializer
    private final JsonDeserializer deserializer = new JsonDeserializer();
    // 업로드된 JSON 파일 이름 목록
    private final List<String> uploadedFileNames = new CopyOnWriteArrayList<>();
    // JSON→AASX 변환 후 생성된 AASX 파일 이름 목록
    private final List<String> uploadedAasxFileNames = new CopyOnWriteArrayList<>();
    // 업로드된 JSON 파일로부터 변환된 Environment 목록
    private final List<Environment> uploadedEnvironments = new CopyOnWriteArrayList<>();

    /**
     * JSON 파일명 → (URL → Deque<FilesMeta>) 매핑
     * - 하나의 URL에 대응하는 여러 FilesMeta 정보를 순서대로 저장
     * - AASX 생성 시 URL을 상대경로로 치환할 때 사용
     */
    private final Map<String, Map<String, Deque<FilesMeta>>> jsonMetaMap = new ConcurrentHashMap<>();;

    /**
     * 한 번의 호출로 URL-only / Revert(embed) 두 Variant를 모두 생성하고, 생성된 AASX 파일명 전체를 리턴
     *
     * @param jsonFiles MultipartFile[] 형태로 업로드된 JSON 파일들
     * @return 생성된 AASX 파일명 리스트 (예: ["example-url.aasx", "example-revert.aasx"])
     */
    public List<String> generateAasxVariants(MultipartFile[] jsonFiles) {
        // 1) URL-only 형식 생성
        uploadJsonFiles(jsonFiles, false);
        List<String> urls = new ArrayList<>(uploadedAasxFileNames);

        // 2) Revert 형식 생성
        uploadJsonFiles(jsonFiles, true);
        List<String> revert = new ArrayList<>(uploadedAasxFileNames);

        // 두 리스트를 합쳐서 반환
        List<String> all = new ArrayList<>();
        all.addAll(urls);
        all.addAll(revert);
        log.info("generateAasxVariants → 생성된 AASX 목록: {}", all);
        return all;
    }

    /**
     * JSON → Environment → AASX 패키지 생성 메인 로직
     * - revertPaths == false → URL-only AASX 생성 (외부 URL 유지)
     * - revertPaths == true → URL을 상대경로로 치환 후 AASX 생성
     *
     * @param jsonFiles   업로드된 JSON 파일들
     * @param revertPaths true: URL을 상대경로로 치환하여 AASX 생성, false: URL-only
     * @return 변환된 Environment 객체 리스트
     */
    public List<Environment> uploadJsonFiles(MultipartFile[] jsonFiles, boolean revertPaths) {
        if (jsonFiles == null || jsonFiles.length == 0) {
            throw new IllegalArgumentException("최소 하나의 JSON 파일을 업로드해야 합니다.");
        }
        // 초기화: 이전 호출 시 저장된 상태를 클리어
        uploadedFileNames.clear();
        uploadedEnvironments.clear();
        uploadedAasxFileNames.clear();

        for (MultipartFile file : jsonFiles) {
            String originalName = file.getOriginalFilename();
            log.info("uploadJsonFiles: 처리 중인 JSON 파일 = {}", originalName);

            // 1️⃣ JSON → Environment
            Environment env = parseEnvironment(file);

            // 2️⃣ JSON 파일명 → DB 메타정보 미리 조회해서 jsonMetaMap에 저장
            saveJsonMetaInfos(originalName, env);

            // 초기 파일/리소스 참조 로그 출력 (디버깅 용도)
            AtomicInteger fileRef = new AtomicInteger();
            AtomicInteger resRef = new AtomicInteger();

            log.info("[Init] 모델 내 File/Resource 참조 위치 출력 시작");

            new AssetAdministrationShellElementWalkerVisitor() {
                @Override
                public void visit(File f) {
                    if (f.getValue() != null) {
                        int idx = fileRef.incrementAndGet();
                        log.info("[Init] File ref #{} → value='{}', objHash={}",
                                idx, f.getValue(), System.identityHashCode(f));
                    }
                }

                @Override
                public void visit(Resource r) {
                    if (r != null && r.getPath() != null) {
                        int idx = resRef.incrementAndGet();
                        log.info("[Init] Resource ref #{} → path='{}', objHash={}",
                                idx, r.getPath(), System.identityHashCode(r));
                    }
                }
            }.visit(env);
            log.info("[Init] 총 File refs: {}, 총 Resource refs: {}", fileRef.get(), resRef.get());
            log.info("[Init] 모델 내 File/Resource 참조 위치 출력 종료");

            // 저장된 JSON 파일명, Environment 리스트에 추가
            uploadedFileNames.add(originalName);
            uploadedEnvironments.add(env);

            // 3️ 실제로 AASX 파일을 생성·저장
            String baseName = deriveBaseName(originalName);
            writeAasx(env, baseName, revertPaths, originalName);
        }
        return new ArrayList<>(uploadedEnvironments);
    }

    /**
     * 1️⃣ JSON 바이너리를 Environment 객체로 파싱
     * 
     * @param jsonFile MultipartFile 형태의 JSON 파일
     * @return 파싱된 Environment 객체
     * @throws RuntimeException 파싱 실패 시
     */
    private Environment parseEnvironment(MultipartFile jsonFile) {
        try {
            byte[] raw = jsonFile.getBytes();
            Environment env = deserializer.read(new ByteArrayInputStream(raw), Environment.class);

            // JSON 내 conceptDescriptions 가 제대로 로드되었는지 로그 확인
            if (env.getConceptDescriptions() != null) {
                log.info("▶ Loaded ConceptDescriptions: count={}", env.getConceptDescriptions().size());
            } else {
                log.warn("▶ No ConceptDescriptions found in JSON");
            }

            return env;
        } catch (IOException | DeserializationException e) {
            log.error("JSON 파싱 오류: {}", e.getMessage());
            throw new RuntimeException("JSON 파싱 실패: " + e.getMessage(), e);
        }
    }

    /**
     * 2️⃣ JSON 이름(jsonName)과 Environment(env)를 받아서
     * URL → FilesMeta Deque 매핑을 생성하는 메소드
     */
    private void saveJsonMetaInfos(String jsonName, Environment env) {
        // AAS–Submodel–File/Resource 매핑을 저장할 임시 Map
        Map<String, Deque<FilesMeta>> map = new HashMap<>();

        // (1) 모든 Submodel을 순회
        for (Submodel sm : env.getSubmodels()) {
            String submodelId = sm.getId();

            // (2) 해당 Submodel을 포함하는 AAS ID를 찾아서 활용
            // (예를 들어, AAS 목록 중 이 submodelId를 직접 참조하고 있는 첫 번째 AAS를 반환)
            String aasId = findAasIdForSubmodel(env, submodelId);

            // (3) AAS 내부의 File/Resource 요소를 순회하며 URL→FilesMeta 매핑
            new AssetAdministrationShellElementWalkerVisitor() {
                @Override
                public void visit(org.eclipse.digitaltwin.aas4j.v3.model.File f) {
                    String url = f.getValue();
                    if (url == null || !url.startsWith("http")) {
                        return;
                    }

                    // findAasIdForSubmodel으로 찾아낸 aasId 사용
                    FilesMeta meta = uploadMapper.selectFileMetaByPath(aasId, submodelId, f.getIdShort());
                    if (meta != null) {
                        // 같은 URL에 여러 개의 메타가 있을 수 있으므로 Deque에 순서대로 넣는다
                        map.computeIfAbsent(url, k -> new ArrayDeque<>()).add(meta);
                    } else {
                        log.warn("DB에 files_meta 없음(File): aasId={}, submodelId={}, idShort={}",
                                aasId, submodelId, f.getIdShort());
                    }
                }

                @Override
                public void visit(org.eclipse.digitaltwin.aas4j.v3.model.Resource r) {
                    if (r == null) {
                        return;
                    }
                    String url = r.getPath();
                    if (url == null || !url.startsWith("http")) {
                        return;
                    }

                    String idShort = ((SubmodelElement) r).getIdShort();
                    // findAasIdForSubmodel으로 찾아낸 aasId 사용
                    FilesMeta meta = uploadMapper.selectFileMetaByPath(aasId, submodelId, idShort);
                    if (meta != null) {
                        map.computeIfAbsent(url, k -> new ArrayDeque<>()).add(meta);
                    } else {
                        log.warn("DB에 files_meta 없음(Resource): aasId={}, submodelId={}, idShort={}",
                                aasId, submodelId, idShort);
                    }
                }
            }.visit(env);
        }

        // 완성된 URL→Deque<FilesMeta> 매핑을 jsonMetaMap에 저장
        jsonMetaMap.put(jsonName, map);
    }

    /**
     * 주어진 submodelId를 참조하는 AAS(Asset Administration Shell)의 ID를 반환
     */
    private String findAasIdForSubmodel(Environment env, String submodelId) {
        if (env.getAssetAdministrationShells() != null) {
            for (var aas : env.getAssetAdministrationShells()) {
                if (aas.getSubmodels() != null) {
                    // AAS가 보유한 Reference 키들 중에 submodelId가 있으면 해당 AAS ID를 반환
                    boolean matches = aas.getSubmodels().stream()
                            .flatMap(ref -> ref.getKeys().stream())
                            .anyMatch(k -> KeyTypes.SUBMODEL.equals(k.getType()) && submodelId.equals(k.getValue()));
                    if (matches) {
                        return aas.getId();
                    }
                }
            }
        }
        // 어느 AAS도 참조하지 않았다면, 리스트의 첫 번째 AAS ID를 반환
        return env.getAssetAdministrationShells().get(0).getId();
    }

    /**
     * 3️ 파일명에서 ".json" 확장자를 제거한 기본 이름을 반환
     * 예: "example.json" → "example"
     *
     * @param originalName JSON 파일의 원래 이름
     * @return 확장자(".json")가 제거된 파일명. 확장자가 없으면 그대로 반환
     */
    private String deriveBaseName(String originalName) {
        if (originalName == null)
            return "unknown";
        return originalName.toLowerCase().endsWith(".json")
                ? originalName.substring(0, originalName.length() - 5)
                : originalName;
    }

    /**
     * 3️ AASX 패키지 파일을 실제로 생성하고 디스크에 저장하는 헬퍼 메소드
     *
     * 1) revertPaths == true: URL을 로컬 상대경로로 치환 (injectInMemoryFiles)
     * 2) 모델 내 File/Resource 전체 참조 로그 출력 (디버깅 용도)
     * 3) InMemoryFile 목록 구성 (getInMemoryFiles)
     * 4) 중복 참조 제거 및 기본 썸네일(thumbnail) 제외
     * 5) 최종 AASX 바이너리를 ByteArrayOutputStream으로 직렬화
     * 6) 지정된 tempPath 디렉토리에 ".aasx" 파일로 저장
     *
     * @param env          변환할 AASX의 Environment 객체
     * @param baseName     AASX 파일명(확장자 제외) 기본 이름
     * @param includeFiles true이면 URL→상대경로 치환 후 InMemoryFile 포함, false이면 URL-only
     * @param jsonName     원본 JSON 파일명(치환 시 jsonMetaMap 조회용)
     */
    private void writeAasx(Environment env, String baseName, boolean includeFiles, String jsonName) {
        try {
            // 🔴 1) Revert(embed) 모드: URL을 상대경로로 치환
            if (includeFiles) {
                injectInMemoryFiles(env, jsonName);
            }

            // 2) 변환 후 모델 내 File/Resource 전체 참조 로그 출력 (디버깅 용도)
            log.info("--- 모델 내 File/Resource 전체 참조 로그 시작 ---");
            AtomicInteger fileIdx = new AtomicInteger(0), resIdx = new AtomicInteger(0);
            new AssetAdministrationShellElementWalkerVisitor() {
                @Override
                public void visit(File f) {
                    if (f.getValue() != null) {
                        int idx = fileIdx.incrementAndGet();
                        log.info("[Visit File #{}] value='{}' (obj={})",
                                idx, f.getValue(), System.identityHashCode(f));
                    }
                }

                @Override
                public void visit(Resource r) {
                    if (r != null && r.getPath() != null) {
                        int idx = resIdx.incrementAndGet();
                        log.info("[Visit Res  #{}] path ='{}' (obj={})",
                                idx, r.getPath(), System.identityHashCode(r));
                    }
                }
            }.visit(env);
            log.info("--- 총 File refs: {}, 총 Resource refs: {} ---", fileIdx.get(), resIdx.get());

            // 🔵 3) InMemoryFile 목록 준비 (includeFiles==true일 때만 실제 파일 포함)
            List<InMemoryFile> inMemFiles = includeFiles
                    ? getInMemoryFiles(env)
                    : Collections.emptyList();

            // 4) default-thumbnail 경로가 InMemoryFile 목록에 남아 있으면 제외
            // AssetInformation.getDefaultThumbnail().getPath()로 참조되는 파일은 이미 “기본 리소스”로
            // 포함되므로,
            // 중복을 방지하기 위해 InMemoryFile 목록에서 미리 제거
            // - AASX 내부에 동일 파일이 여러 번 들어가지 않도록 최적화
            String defaultThumb = env.getAssetAdministrationShells().stream()
                    .map(aas -> aas.getAssetInformation())
                    .filter(ai -> ai != null && ai.getDefaultThumbnail() != null)
                    .map(ai -> ai.getDefaultThumbnail().getPath())
                    .filter(p -> p != null && !p.isBlank())
                    .findFirst()
                    .orElse(null);

            if (defaultThumb != null) {
                String normThumb = fileUploadService.normalizePath(defaultThumb);
                inMemFiles.removeIf(imf -> fileUploadService.normalizePath(imf.getPath()).equals(normThumb));
                log.info("default-thumbnail '{}' (normalized='{}') 은 inMemFiles 에서 제거", defaultThumb, normThumb);
            }

            // 5) 중복된 InMemoryFile(동일 path) 제거
            if (includeFiles && !inMemFiles.isEmpty()) {
                Map<String, InMemoryFile> deduped = inMemFiles.stream()
                        .collect(Collectors.toMap(
                                InMemoryFile::getPath,
                                f -> f,
                                (a, b) -> a));
                inMemFiles = new ArrayList<>(deduped.values());
            }

            // 6) InMemoryFile 목록 로그 출력
            List<String> paths = inMemFiles.stream()
                    .map(InMemoryFile::getPath)
                    .collect(Collectors.toList());
            log.info(">>> InMemoryFiles [{}개]: {}", paths.size(), paths);

            // 7) AASX 파일명 결정: URL-only → "-url.aasx", Revert/embed → "-revert.aasx"
            String suffix = includeFiles ? "-revert" : "-url";
            String targetName = baseName + suffix + ".aasx";

            // 8) AASX 패키징 직렬화
            AASXSerializer serializer = new AASXSerializer(new XmlSerializer());
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            serializer.write(env, inMemFiles, baos);

            // 9) tempPath 디렉토리에 AASX 파일로 저장
            Path outPath = Paths.get(tempPath, targetName);
            Files.createDirectories(outPath.getParent());
            Files.write(outPath, baos.toByteArray());

            uploadedAasxFileNames.add(targetName);
            log.info("AASX 패키지 생성 및 저장 완료: {}", outPath);

        } catch (Exception e) {
            log.error("AASX 패키지 생성 실패 for {}: {}", baseName, e.getMessage());
        }
    }

    /**
     * 🔴 Revert(embed) 모드에서 AASX 내 File/Resource 요소가 보유한 URL을
     * DB에서 조회한 FilesMeta 정보를 기반으로 실제 상대경로(파일시스템 경로)로 치환
     *
     * 1) AssetAdministrationShellElementWalkerVisitor 로 모델 내 모든 File/Resource 순회
     * 2) URL 값이 http로 시작하면 jsonMetaMap 에서 해당 URL에 매핑된 Deque<FilesMeta>를 꺼냄
     * 3) FilesMeta.getPath 값을 상대경로로 사용하여
     * - uploadPath 디렉토리에서 실제 파일을 tempPath/상대경로로 복사
     * - File/Resource 객체의 value/path 필드를 상대경로로 설정
     * - semanticId 필드는 null로 설정하여 직렬화 시 오류 방지
     *
     * @param env      변환 대상 Environment 객체
     * @param jsonName 원본 JSON 파일명 (jsonMetaMap 조회 키)
     */
    private void injectInMemoryFiles(Environment env, String jsonName) {
        // JSON 이름에 매핑된 URL→Deque<FilesMeta> 맵 가져오기
        Map<String, Deque<FilesMeta>> metaMap = jsonMetaMap.getOrDefault(jsonName, Map.of());

        new AssetAdministrationShellElementWalkerVisitor() {
            @Override
            public void visit(File fileEl) {
                String url = fileEl.getValue();
                if (url == null || !url.startsWith("http"))
                    return;

                Deque<FilesMeta> deque = metaMap.get(url);
                if (deque == null || deque.isEmpty()) {
                    log.warn("메타 없음(File): json={} url={}", jsonName, url);
                    return;
                }
                FilesMeta meta = deque.pollFirst(); // 해당 URL에 대응하는 첫 번째 FilesMeta 정보를 꺼냄

                log.info("inject(File) 매핑 확인 → url='{}', aasId='{}', submodelId='{}', idShort='{}', path='{}'",
                        url, meta.getAasId(), meta.getSubmodelId(), meta.getIdShort(), meta.getPath());

                try {
                    String hash = meta.getHash();
                    String ext = meta.getExtension();
                    String relPath = meta.getPath(); // DB에 저장된 상대경로

                    // uploadPath/{hash}{ext} 에서 실제 파일을 읽어 tempPath/{relPath} 로 복사
                    Path dst = Paths.get(tempPath, relPath);
                    Files.createDirectories(dst.getParent());
                    Files.copy(Paths.get(uploadPath, hash + ext),
                            dst, StandardCopyOption.REPLACE_EXISTING);

                    // 모델 내 File 요소의 value를 상대경로로 치환
                    fileEl.setValue(relPath);
                    log.info("치환 완료(File): {} → {}", url, relPath);

                } catch (Exception ex) {
                    log.error("injectInMemoryFiles 오류(File) for {}: {}", url, ex.getMessage());
                }
            }

            @Override
            public void visit(Resource res) {
                if (res == null)
                    return;
                String url = res.getPath();
                if (url == null || !url.startsWith("http"))
                    return;

                Deque<FilesMeta> deque = metaMap.get(url);
                if (deque == null || deque.isEmpty()) {
                    log.warn("메타 없음(Resource): json={} url={}", jsonName, url);
                    return;
                }
                FilesMeta meta = deque.pollFirst();

                log.info("inject(Resource) 매핑 확인 → url='{}', aasId='{}', submodelId='{}', idShort='{}', path='{}'",
                        url, meta.getAasId(), meta.getSubmodelId(), meta.getIdShort(), meta.getPath());

                try {
                    String hash = meta.getHash();
                    String ext = meta.getExtension();
                    String relPath = meta.getPath();

                    // 실제 파일 복사
                    Path dst = Paths.get(tempPath, relPath);
                    Files.createDirectories(dst.getParent());
                    Files.copy(Paths.get(uploadPath, hash + ext),
                            dst, StandardCopyOption.REPLACE_EXISTING);

                    // 모델 내 Resource 요소의 path를 상대경로로 치환
                    res.setPath(relPath);
                    log.info("치환 완료(Resource): {} → {}", url, relPath);

                } catch (Exception ex) {
                    log.error("injectInMemoryFiles(Resource) 오류 for {}: {}", url, ex.getMessage());
                }
            }
        }.visit(env);
    }

    /**
     * 🔵 injectInMemoryFiles()가 복사해 놓은 tempPath/{path} 파일들을 읽어
     * InMemoryFile 인스턴스 목록으로 반환
     * 
     * AASXSerializer.write 시 실제 파일 컨텐츠를 포함하려면 InMemoryFile 리스트가 필요
     *
     * @param env AASX 생성 대상 Environment 객체
     * @return InMemoryFile 목록 (파일 바이트 + 경로)
     */
    public List<InMemoryFile> getInMemoryFiles(Environment env) {
        List<InMemoryFile> files = new ArrayList<>();
        new AssetAdministrationShellElementWalkerVisitor() {
            @Override
            public void visit(File fileEl) {
                String val = fileEl.getValue();
                if (val != null && !val.startsWith("http")) {
                    Path p = Paths.get(tempPath, val);
                    try {
                        files.add(new InMemoryFile(Files.readAllBytes(p), val));
                        log.debug("InMemoryFile 추가(File): {}", val);
                    } catch (IOException e) {
                        log.warn("InMemoryFile 생성 실패 ({}): {}", val, e.getMessage());
                    }
                }
            }

            @Override
            public void visit(Resource res) {
                String path = res != null ? res.getPath() : null;
                if (path != null && !path.startsWith("http")) {
                    Path p = Paths.get(tempPath, path);
                    try {
                        files.add(new InMemoryFile(Files.readAllBytes(p), path));
                        log.debug("InMemoryFile 추가(Resource): {}", path);
                    } catch (IOException e) {
                        log.warn("InMemoryFile 생성 실패 ({}): {}", path, e.getMessage());
                    }
                }
            }
        }.visit(env);
        return files;
    }

    // 이하 getter들…
    public List<String> getUploadedAasxFileNames() {
        return new ArrayList<>(uploadedAasxFileNames);
    }

    public List<String> getUploadedJsonFileNames() {
        return new ArrayList<>(uploadedFileNames);
    }

    public List<Environment> getUploadedEnvironments() {
        return new ArrayList<>(uploadedEnvironments);
    }

}
