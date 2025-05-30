package com.aasx.transformer.download.service;

import org.eclipse.digitaltwin.aas4j.v3.model.AssetAdministrationShell;
import org.eclipse.digitaltwin.aas4j.v3.model.Environment;
import org.eclipse.digitaltwin.aas4j.v3.model.KeyTypes;
import org.eclipse.digitaltwin.aas4j.v3.model.Reference;
import org.eclipse.digitaltwin.aas4j.v3.model.Submodel;
import org.eclipse.digitaltwin.aas4j.v3.model.SubmodelElement;
import org.eclipse.digitaltwin.aas4j.v3.model.SubmodelElementCollection;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import com.aasx.transformer.deserializer.AASXFileDeserializer;
import com.aasx.transformer.upload.dto.FilesMeta;
import com.aasx.transformer.upload.mapper.UploadMapper;
import com.aasx.transformer.upload.service.FileUploadService;
import com.aasx.transformer.upload.service.JsonToAASXService;

import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Service
@Slf4j
public class FileDownloadService {

    @Autowired
    private UploadMapper uploadMapper;

    @Autowired
    private FileUploadService fileUploadService;

    @Autowired
    private JsonToAASXService jsonToAasxService;

    @Autowired
    private AASXFileDeserializer aasxFileDeserializer;

    @Value("${upload.path}")
    private String uploadPath;

    /**
     * ✅ JSON→AASX 변환된 환경 전용 메소드
     *    주어진 JSON 파일명에 해당하는 Environment에서 메타를 조회
     */
    public List<FilesMeta> getJsonConvertedFileMetas(String packageFileName) {
        List<String> jsonNames = jsonToAasxService.getUploadedJsonFileNames();
        int idx = jsonNames.indexOf(packageFileName);
        if (idx < 0) {
            log.warn("JSON→AASX 변환 환경에서 '{}' 을(를) 찾을 수 없습니다.", packageFileName);
            return Collections.emptyList();
        }
        Environment env = jsonToAasxService.getUploadedEnvironments().get(idx);
        return collectMetas(env);
    }

    /**
     * ✅ 특정 패키지 파일 이름에 해당하는 Environment의 첨부파일 메타 정보를 조회
     *    (기존 AASX 업로드 환경에서 처리)
     */
    public List<FilesMeta> getFileMetasByPackageFileName(String packageFileName) {
        List<String> fileNames = fileUploadService.getUploadedFileNames();
        List<Environment> environments = fileUploadService.getUploadedEnvironments();

        int index = fileNames.indexOf(packageFileName);
        if (index < 0) {
            log.warn("패키지 파일 '{}' 에 해당하는 Environment를 찾을 수 없습니다.", packageFileName);
            return Collections.emptyList();
        }
        Environment environment = environments.get(index);
        return collectMetas(environment);
    }

    /**
     * ✅ Environment 객체로부터 Submodel 순회, File 요소 추출, DefaultThumbnail 처리까지
     *    공통 메타 추출 로직
     */
    private List<FilesMeta> collectMetas(Environment environment) {
        List<FilesMeta> metas = new ArrayList<>();

        if (environment.getSubmodels() != null) {
            for (Submodel submodel : environment.getSubmodels()) {
                String submodelId = submodel.getId();
                String aasId = findAasIdForSubmodel(environment, submodelId);
                collectFileMetasRecursive(submodel.getSubmodelElements(), aasId, submodelId, metas);
            }
        }

        // --- Asset default thumbnail (reflection) 처리 ---
        try {
            for (AssetAdministrationShell shell : environment.getAssetAdministrationShells()) {
                Object assetInfo = shell.getAssetInformation();
                if (assetInfo == null) continue;

                Method mThumb = assetInfo.getClass().getMethod("getDefaultThumbnail");
                Object dataRes = mThumb.invoke(assetInfo);
                if (dataRes == null) continue;

                String thumbPath = null;
                try {
                    Method mVal = dataRes.getClass().getMethod("getValue");
                    Object raw = mVal.invoke(dataRes);
                    if (raw instanceof String) thumbPath = (String) raw;
                } catch (Exception e1) {
                    try {
                        Method mP = dataRes.getClass().getMethod("getPath");
                        Object raw2 = mP.invoke(dataRes);
                        if (raw2 instanceof String) thumbPath = (String) raw2;
                    } catch (Exception ignored) {}
                }
                if (thumbPath == null) continue;

                String aasId = shell.getId();
                Method mGlobal = assetInfo.getClass().getMethod("getGlobalAssetId");
                String globalAssetId = (String) mGlobal.invoke(assetInfo);
                String idShort = new File(thumbPath).getName();

                FilesMeta thumbMeta = uploadMapper.selectFileMetaByPath(aasId, globalAssetId, idShort);
                if (thumbMeta != null && metas.stream().noneMatch(m -> m.getHash().equals(thumbMeta.getHash()))) {
                    try {
                        Method mCT = dataRes.getClass().getMethod("getContentType");
                        Object ctRaw = mCT.invoke(dataRes);
                        if (ctRaw instanceof String) thumbMeta.setContentType((String) ctRaw);
                    } catch (NoSuchMethodException | ClassCastException ignored) {}

                    int dot = thumbPath.lastIndexOf('.');
                    if (dot >= 0) thumbMeta.setExtension(thumbPath.substring(dot));

                    metas.add(thumbMeta);
                }
                break;
            }
        } catch (Exception e) {
            log.warn("DefaultThumbnail reflection 처리 중 오류: {}", e.toString());
        }

        log.info("Environment '{}' 에서 추출된 메타 총 {}건", environment.getAssetAdministrationShells().get(0).getId(), metas.size());
        return metas;
    }

    /**
     * ✅ 주어진 submodelId 를 참조하는 AAS ID 반환 (없으면 첫 번째 AAS)
     */
    private String findAasIdForSubmodel(Environment env, String submodelId) {
        if (env.getAssetAdministrationShells() != null) {
            for (AssetAdministrationShell aas : env.getAssetAdministrationShells()) {
                if (aas.getSubmodels() != null) {
                    for (Reference ref : aas.getSubmodels()) {
                        boolean matches = ref.getKeys().stream()
                                .anyMatch(k -> KeyTypes.SUBMODEL.equals(k.getType()) && submodelId.equals(k.getValue()));
                        if (matches) return aas.getId();
                    }
                }
            }
        }
        return env.getAssetAdministrationShells().get(0).getId();
    }

    /**
     * ✅ SubmodelElement 재귀 순회하며 File 요소를 찾아 DB 메타 조회
     */
    @SuppressWarnings("unchecked")
    private void collectFileMetasRecursive(List<SubmodelElement> elements, String aasId, String submodelId, List<FilesMeta> metas) {
        if (elements == null) return;
        for (SubmodelElement element : elements) {
            if (element instanceof org.eclipse.digitaltwin.aas4j.v3.model.File) {
                org.eclipse.digitaltwin.aas4j.v3.model.File fileEl = (org.eclipse.digitaltwin.aas4j.v3.model.File) element;
                String idShort = fileEl.getIdShort();
                if (fileEl.getValue() == null || fileEl.getValue().trim().isEmpty()) {
                    log.info("빈 file value 건너뜀, idShort: {}", idShort);
                    continue;
                }
                FilesMeta meta = uploadMapper.selectFileMetaByPath(aasId, submodelId, idShort);
                if (meta != null) metas.add(meta);
                else log.warn("DB에서 메타를 찾지 못함 (aasId={}, submodelId={}, idShort={})", aasId, submodelId, idShort);
            } else if (element instanceof SubmodelElementCollection) {
                collectFileMetasRecursive(((SubmodelElementCollection) element).getValue(), aasId, submodelId, metas);
            } else {
                try {
                    Method gv = element.getClass().getMethod("getValue");
                    if (List.class.isAssignableFrom(gv.getReturnType())) {
                        List<SubmodelElement> child = (List<SubmodelElement>) gv.invoke(element);
                        collectFileMetasRecursive(child, aasId, submodelId, metas);
                    }
                } catch (Exception e) {
                    log.warn("자식 요소 탐색 중 예외 발생: {}", e.getMessage());
                }
            }
        }
    }

    /**
     * ✅ Environment 객체를 JSON으로 직렬화 후 Resource 반환
     */
    public Resource downloadEnvironmentAsJson(Environment environment, String originalFileName) {
        log.info("downloadEnvironmentAsJson 호출 - originalFileName: {}", originalFileName);
        try {
            String json = aasxFileDeserializer.serializeEnvironmentToJson(environment);
            return new ByteArrayResource(json.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            log.error("Environment JSON 파일 생성 실패", e);
            throw new RuntimeException("Environment JSON 파일 생성 실패", e);
        }
    }

    /**
     * ✅ 해시로 파일 조회 후 Resource 반환
     */
    public Resource downloadFileByHash(String hash) {
        FilesMeta meta = uploadMapper.selectOneFileMetaByHash(hash);
        if (meta == null) {
            log.error("해당 해시의 파일 메타 정보가 없습니다: {}", hash);
            throw new RuntimeException("파일을 찾을 수 없습니다.");
        }
        File file = new File(uploadPath, hash + meta.getExtension());
        if (!file.exists()) {
            log.error("물리 파일이 존재하지 않습니다: {}", file.getAbsolutePath());
            throw new RuntimeException("파일을 찾을 수 없습니다.");
        }
        log.info("다운로드할 파일 경로: {}", file.getAbsolutePath());
        return new FileSystemResource(file);
    }

    /**
     * ✅ 단일 FilesMeta 조회
     */
    public FilesMeta getMetaByHash(String hash) {
        return uploadMapper.selectOneFileMetaByHash(hash);
    }
}
