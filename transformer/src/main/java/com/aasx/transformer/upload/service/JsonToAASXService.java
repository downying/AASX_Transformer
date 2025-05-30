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
import org.eclipse.digitaltwin.aas4j.v3.model.Environment;
import org.eclipse.digitaltwin.aas4j.v3.model.File;
import org.eclipse.digitaltwin.aas4j.v3.model.Resource;
import org.eclipse.digitaltwin.aas4j.v3.model.SubmodelElement;
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

    private final JsonDeserializer deserializer = new JsonDeserializer();

    // 업로드된 JSON 파일 이름 목록
    private final List<String> uploadedFileNames = new CopyOnWriteArrayList<>();
    // JSON→AASX 변환 후 생성된 AASX 파일 이름 목록
    private final List<String> uploadedAasxFileNames = new CopyOnWriteArrayList<>();
    // 업로드된 JSON 파일로부터 변환된 Environment 목록
    private final List<Environment> uploadedEnvironments = new CopyOnWriteArrayList<>();

    // JSON 파일명 → (URL → Deque<FilesMeta>) 맵
    // 1. 하나의 URL에 매핑된 여러 FilesMeta를
    // 2. 저장한 순서대로(addLast)
    // 3. 치환 시마다 하나씩(pollFirst) 꺼내 처리
    private final Map<String, Map<String, Deque<FilesMeta>>> jsonMetaMap = new ConcurrentHashMap<>();;

    /**
     * 한 번의 호출로 URL-only / embed-files 두 variant 생성
     * 반환된 리스트에는 "-url.aasx" 와 "-embed.aasx" 가 섞여 있습니다.
     */
    public List<String> generateAasxVariants(MultipartFile[] jsonFiles) {
        // URL-only
        uploadJsonFiles(jsonFiles, false);
        List<String> urls = new ArrayList<>(uploadedAasxFileNames);

        // embed-files
        uploadJsonFiles(jsonFiles, true);
        List<String> embeds = new ArrayList<>(uploadedAasxFileNames);

        List<String> all = new ArrayList<>();
        all.addAll(urls);
        all.addAll(embeds);
        log.info("generateAasxVariants → 생성된 AASX 목록: {}", all);
        return all;
    }

    /**
     * JSON → Environment → AASX 패키지 생성
     *
     * @param jsonFiles   업로드된 JSON 파일들
     * @param revertPaths true: URL→상대경로(embed), false: URL-only
     */
    public List<Environment> uploadJsonFiles(MultipartFile[] jsonFiles, boolean revertPaths) {
        if (jsonFiles == null || jsonFiles.length == 0) {
            throw new IllegalArgumentException("최소 하나의 JSON 파일을 업로드해야 합니다.");
        }
        uploadedFileNames.clear();
        uploadedEnvironments.clear();
        uploadedAasxFileNames.clear();

        for (MultipartFile file : jsonFiles) {
            String originalName = file.getOriginalFilename();
            log.info("uploadJsonFiles: 처리 중인 JSON 파일 = {}", originalName);

            // 1) JSON → Environment
            Environment env = parseEnvironment(file);

            // ★ JSON 파일명 → DB 메타정보 미리 조회해서 jsonMetaMap에 저장
            saveJsonMetaInfos(originalName, env);

            // 확인용 로그
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

            uploadedFileNames.add(originalName);
            uploadedEnvironments.add(env);

            // 2) semanticId 제거
            clearSemanticIds(env);

            // 3) AASX Serializer로 패키징·저장
            String baseName = deriveBaseName(originalName);
            writeAasx(env, baseName, revertPaths, originalName);
        }
        return new ArrayList<>(uploadedEnvironments);
    }

    /** 기본 모드 (revertPaths=false) */
    public List<Environment> uploadJsonFiles(MultipartFile[] jsonFiles) {
        return uploadJsonFiles(jsonFiles, false);
    }

    /** ".json" 확장자 제거 헬퍼 */
    private String deriveBaseName(String originalName) {
        if (originalName == null)
            return "unknown";
        return originalName.toLowerCase().endsWith(".json")
                ? originalName.substring(0, originalName.length() - 5)
                : originalName;
    }

    /**
     * AASX 쓰기헬퍼
     */
    private void writeAasx(Environment env, String baseName, boolean includeFiles, String jsonName) {
        try {
            // 1) embed 모드: URL 치환 + 중복 참조 제거
            if (includeFiles) {
                injectInMemoryFiles(env, jsonName);
                // removeDuplicateRefs(env);
            }

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

            // 3) InMemoryFile 준비
            List<InMemoryFile> inMemFiles = includeFiles
                    ? getInMemoryFiles(env)
                    : Collections.emptyList();

            // default-thumbnail 경로 가져오기
            String defaultThumb = env.getAssetAdministrationShells().stream()
                    .map(aas -> aas.getAssetInformation())
                    .filter(ai -> ai != null && ai.getDefaultThumbnail() != null)
                    .map(ai -> ai.getDefaultThumbnail().getPath())
                    .filter(p -> p != null && !p.isBlank())
                    .findFirst()
                    .orElse(null);

            if (defaultThumb != null) {
                // FileUploadService 의 normalizePath 로 비교
                String normThumb = fileUploadService.normalizePath(defaultThumb);
                inMemFiles.removeIf(imf -> fileUploadService.normalizePath(imf.getPath()).equals(normThumb));
                log.info("default-thumbnail '{}' (normalized='{}') 은 inMemFiles 에서 제거", defaultThumb, normThumb);
            }

            // 4) 중복 제거 (path 기준)
            if (includeFiles && !inMemFiles.isEmpty()) {
                Map<String, InMemoryFile> deduped = inMemFiles.stream()
                        .collect(Collectors.toMap(
                                InMemoryFile::getPath,
                                f -> f,
                                (a, b) -> a));
                inMemFiles = new ArrayList<>(deduped.values());
            }

            // 5) InMemoryFile 목록 로그
            List<String> paths = inMemFiles.stream()
                    .map(InMemoryFile::getPath)
                    .collect(Collectors.toList());
            log.info(">>> InMemoryFiles [{}개]: {}", paths.size(), paths);

            // 6) 파일명 결정
            String suffix = includeFiles ? "-embed" : "-url";
            String targetName = baseName + suffix + ".aasx";

            // 7) 패키징
            AASXSerializer serializer = new AASXSerializer(new XmlSerializer());
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            serializer.write(env, inMemFiles, baos);

            // 8) 디스크에 쓰기
            Path outPath = Paths.get(tempPath, targetName);
            Files.createDirectories(outPath.getParent());
            Files.write(outPath, baos.toByteArray());

            uploadedAasxFileNames.add(targetName);
            log.info("AASX 패키지 생성 및 저장 완료: {}", outPath);

        } catch (Exception e) {
            log.error("AASX 패키지 생성 실패 for {}: {}", baseName, e.getMessage());
        }
    }

    /** env 안의 중복 File/Resource 참조를 미리 제거 */
    /*
     * private void removeDuplicateRefs(Environment env) {
     * Set<String> seen = new HashSet<>();
     * new AssetAdministrationShellElementWalkerVisitor() {
     * 
     * @Override
     * public void visit(org.eclipse.digitaltwin.aas4j.v3.model.File fileEl) {
     * String val = fileEl.getValue();
     * if (val != null && !val.startsWith("http") && !seen.add(val)) {
     * fileEl.setValue(null);
     * }
     * }
     * 
     * @Override
     * public void visit(org.eclipse.digitaltwin.aas4j.v3.model.Resource res) {
     * if (res == null)
     * return;
     * String path = res.getPath();
     * if (path != null && !path.startsWith("http") && !seen.add(path)) {
     * res.setPath(null);
     * }
     * }
     * }.visit(env);
     * }
     */

    /** JSON을 Environment 객체로 파싱 */
    private Environment parseEnvironment(MultipartFile jsonFile) {
        try {
            byte[] raw = jsonFile.getBytes();
            log.info("JSON snippet: {}...", new String(raw, 0, Math.min(200, raw.length)));
            return deserializer.read(new ByteArrayInputStream(raw), Environment.class);
        } catch (IOException | DeserializationException e) {
            log.error("JSON 파싱 오류: {}", e.getMessage());
            throw new RuntimeException("JSON 파싱 실패: " + e.getMessage(), e);
        }
    }

    /**
     * embed 모드에서 URL→상대경로 치환 (DB에 저장된 path 컬럼 사용)
     */
    private void injectInMemoryFiles(Environment env, String jsonName) {
        Map<String, Deque<FilesMeta>> metaMap = jsonMetaMap.getOrDefault(jsonName, Map.of());

        new AssetAdministrationShellElementWalkerVisitor() {
            @Override
            public void visit(org.eclipse.digitaltwin.aas4j.v3.model.File fileEl) {
                String url = fileEl.getValue();
                if (url == null || !url.startsWith("http"))
                    return;

                Deque<FilesMeta> deque = metaMap.get(url);
                if (deque == null || deque.isEmpty()) {
                    log.warn("메타 없음(File): json={} url={}", jsonName, url);
                    return;
                }
                FilesMeta meta = deque.pollFirst();

                log.info("inject(File) 매핑 확인 → url='{}', aasId='{}', submodelId='{}', idShort='{}', path='{}'",
                        url, meta.getAasId(), meta.getSubmodelId(), meta.getIdShort(), meta.getPath());

                try {
                    String hash = meta.getHash();
                    String ext = meta.getExtension();
                    String relPath = meta.getPath();

                    Path dst = Paths.get(tempPath, relPath);
                    Files.createDirectories(dst.getParent());
                    Files.copy(Paths.get(uploadPath, hash + ext),
                            dst, StandardCopyOption.REPLACE_EXISTING);

                    fileEl.setValue(relPath);
                    fileEl.setSemanticId(null);
                    log.info("치환 완료(File): {} → {}", url, relPath);

                } catch (Exception ex) {
                    log.error("injectInMemoryFiles 오류(File) for {}: {}", url, ex.getMessage());
                }
            }

            @Override
            public void visit(org.eclipse.digitaltwin.aas4j.v3.model.Resource res) {
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

                    Path dst = Paths.get(tempPath, relPath);
                    Files.createDirectories(dst.getParent());
                    Files.copy(Paths.get(uploadPath, hash + ext),
                            dst, StandardCopyOption.REPLACE_EXISTING);

                    res.setPath(relPath);
                    log.info("치환 완료(Resource): {} → {}", url, relPath);

                } catch (Exception ex) {
                    log.error("injectInMemoryFiles(Resource) 오류 for {}: {}", url, ex.getMessage());
                }
            }
        }.visit(env);
    }

    /** semanticId 가 남아 있으면 AASXSerializer가 에러를 뱉기 때문에 제거 */
    private void clearSemanticIds(Environment env) {
        if (env.getSubmodels() != null) {
            env.getSubmodels().forEach(sm -> clearIdsRecursive(sm.getSubmodelElements()));
        }
    }

    @SuppressWarnings("unchecked")
    private void clearIdsRecursive(List<?> elements) {
        if (elements == null)
            return;
        for (Object el : elements) {
            if (el instanceof org.eclipse.digitaltwin.aas4j.v3.model.SubmodelElement) {
                try {
                    var sme = (org.eclipse.digitaltwin.aas4j.v3.model.SubmodelElement) el;
                    sme.getClass()
                            .getMethod("setSemanticId", org.eclipse.digitaltwin.aas4j.v3.model.Reference.class)
                            .invoke(sme, new Object[] { null });
                } catch (Exception ignore) {
                }
            }
            if (el instanceof org.eclipse.digitaltwin.aas4j.v3.model.SubmodelElementCollection) {
                clearIdsRecursive(((org.eclipse.digitaltwin.aas4j.v3.model.SubmodelElementCollection) el).getValue());
            } else {
                try {
                    var gv = el.getClass().getMethod("getValue");
                    var val = gv.invoke(el);
                    if (val instanceof List<?>)
                        clearIdsRecursive((List<?>) val);
                } catch (Exception ignore) {
                }
            }
        }
    }

    /**
     * injectInMemoryFiles() 가 복사해 놓은
     * tempPath/name.ext 파일들을 InMemoryFile 로 읽어서 반환
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

    private void saveJsonMetaInfos(String jsonName, Environment env) {
        String aasId = env.getAssetAdministrationShells().get(0).getId();
        Map<String, Deque<FilesMeta>> map = new HashMap<>();

        for (var sm : env.getSubmodels()) {
            String submodelId = sm.getId();
            new AssetAdministrationShellElementWalkerVisitor() {
                @Override
                public void visit(org.eclipse.digitaltwin.aas4j.v3.model.File f) {
                    String url = f.getValue();
                    if (url == null || !url.startsWith("http"))
                        return;

                    FilesMeta meta = uploadMapper.selectFileMetaByPath(aasId, submodelId, f.getIdShort());
                    if (meta != null) {
                        // URL 하나당 여러 Meta를 Deque에 순서대로 담는다
                        map.computeIfAbsent(url, k -> new ArrayDeque<>())
                                .add(meta);
                    } else {
                        log.warn("DB에 files_meta 없음: {}, {}, {}", aasId, submodelId, f.getIdShort());
                    }
                }

                @Override
                public void visit(org.eclipse.digitaltwin.aas4j.v3.model.Resource r) {
                    if (r == null)
                        return;
                    String url = r.getPath();
                    if (url == null || !url.startsWith("http"))
                        return;

                    String idShort = ((SubmodelElement) r).getIdShort();
                    FilesMeta meta = uploadMapper.selectFileMetaByPath(aasId, submodelId, idShort);
                    if (meta != null) {
                        map.computeIfAbsent(url, k -> new ArrayDeque<>())
                                .add(meta);
                    } else {
                        log.warn("DB에 files_meta 없음(Resource): {}, {}, {}", aasId, submodelId, idShort);
                    }
                }
            }.visit(env);
        }

        jsonMetaMap.put(jsonName, map);
    }
}
