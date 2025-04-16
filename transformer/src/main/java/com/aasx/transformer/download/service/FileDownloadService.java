package com.aasx.transformer.download.service;

import org.eclipse.digitaltwin.aas4j.v3.model.Environment;
import org.eclipse.digitaltwin.aas4j.v3.model.Submodel;
import org.eclipse.digitaltwin.aas4j.v3.model.SubmodelElement;
import org.eclipse.digitaltwin.aas4j.v3.model.SubmodelElementCollection;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import com.aasx.transformer.upload.dto.FilesMeta;
import com.aasx.transformer.upload.mapper.UploadMapper;
import com.aasx.transformer.upload.service.FileUploadService;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.extern.slf4j.Slf4j;

import java.io.File;
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

    @Value("${upload.path}")
    private String uploadPath;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * ✅ 특정 패키지 파일 이름에 해당하는 Environment의 첨부파일 메타 정보를 조회
     * Environment 내의 각 Submodel을 순회하여 File 요소를 확인하고,
     * 각 File 요소의 복합키 (aasId, submodelId, idShort)를 이용해 DB에서 파일 메타 정보를 조회
     */
    public List<FilesMeta> getFileMetasByPackageFileName(String packageFileName) {
        // fileUploadService의 getter 메서드를 사용하여 업로드된 파일 이름과 Environment 목록을 가져옴
        List<String> fileNames = fileUploadService.getUploadedFileNames();
        List<Environment> environments = fileUploadService.getUploadedEnvironments();

        int index = fileNames.indexOf(packageFileName);
        if (index < 0) {
            log.warn("패키지 파일 '{}' 에 해당하는 Environment를 찾을 수 없습니다.", packageFileName);
            return Collections.emptyList();
        }
        Environment environment = environments.get(index);
        List<FilesMeta> metas = new ArrayList<>();
        String aasId = environment.getAssetAdministrationShells().get(0).getId();

        if (environment.getSubmodels() != null) {
            for (Submodel submodel : environment.getSubmodels()) {
                String submodelId = submodel.getId();
                // 재귀적으로 모든 File 요소를 수집
                collectFileMetasRecursive(submodel.getSubmodelElements(), aasId, submodelId, metas);
            }
        }
        log.info("패키지 파일 '{}' (AAS ID {}) 에 해당하는 첨부파일 메타 개수: {}", packageFileName, aasId, metas.size());
        return metas;
    }

    /**
     * ✅ 재귀적으로 SubmodelElement 리스트(혹은 SubmodelElementCollection 내부)를 순회하며,
     * File 요소를 찾고, 해당 요소의 복합키 (aasId, submodelId, idShort)를 이용해 DB에서 FilesMeta를 조회한
     * 후 리스트에 추가
     */
    @SuppressWarnings("unchecked")
    private void collectFileMetasRecursive(List<SubmodelElement> elements, String aasId, String submodelId, List<FilesMeta> metas) {
        if (elements == null)
            return;

        for (SubmodelElement element : elements) {
            // 1️⃣ 요소가 File 타입인 경우 처리
            if (element instanceof org.eclipse.digitaltwin.aas4j.v3.model.File) {
                org.eclipse.digitaltwin.aas4j.v3.model.File fileElement = (org.eclipse.digitaltwin.aas4j.v3.model.File) element;
                String idShort = fileElement.getIdShort();
                // value가 비어있는 경우는 건너뛰도록 할 수 있다면 아래와 같이 체크
                if (fileElement.getValue() == null || fileElement.getValue().trim().isEmpty()) {
                    log.info("빈 file value 건너뜀, idShort: {}", idShort);
                    continue;
                }
                // DB에서 파일 메타 조회 및 리스트 추가
                FilesMeta meta = uploadMapper.selectFileMetaByPath(aasId, submodelId, idShort);
                if (meta != null) {
                    metas.add(meta);
                } else {
                    log.warn("DB에서 복합키 (aasId: {}, submodelId: {}, idShort: {}) 의 파일 메타를 찾지 못함.", aasId, submodelId,
                            idShort);
                }
            }

            // 2️⃣ 요소가 SubmodelElementCollection인 경우 처리
            else if (element instanceof SubmodelElementCollection) {
                SubmodelElementCollection collection = (SubmodelElementCollection) element;
                // 재귀적으로 하위 요소 탐색
                collectFileMetasRecursive(collection.getValue(), aasId, submodelId, metas);
            }

            // 3️⃣ 그 밖의 다른 컨테이너 타입 처리
            // reflection을 사용해서 getValue()로 자식 요소를 가져오기
            else {
                try {
                    java.lang.reflect.Method getValueMethod = element.getClass().getMethod("getValue");
                    if (getValueMethod != null && List.class.isAssignableFrom(getValueMethod.getReturnType())) {
                        List<SubmodelElement> childElements = (List<SubmodelElement>) getValueMethod.invoke(element);
                        collectFileMetasRecursive(childElements, aasId, submodelId, metas);
                    }
                } catch (Exception e) {
                    log.warn("자식 요소 탐색 중 예외 발생: {}", e.getMessage());
                }
            }
        }
    }

    // ✅ 업데이트된 Environment 객체를 JSON으로 변환 후 임시 파일로 반환
    public Resource downloadEnvironmentAsJson(Environment environment, String originalFileName) {
        log.info("downloadEnvironmentAsJson 호출 - originalFileName: {}", originalFileName);
        try {
            // 원본 파일 이름에서 확장자(.aasx 등)를 제거 gn ".json" 확장자 추가
            String baseName = originalFileName;
            int dotIndex = originalFileName.lastIndexOf(".");
            if (dotIndex > 0) {
                baseName = originalFileName.substring(0, dotIndex);
            }
            String tempFileName = baseName + ".json";

            // 업로드 경로에 JSON 파일 생성
            File tempFile = new File(uploadPath, tempFileName);
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(tempFile, environment);
            log.info("Environment JSON 임시 파일 생성: {}", tempFile.getAbsolutePath());

            // FileSystemResource로 반환
            return new FileSystemResource(tempFile);
        } catch (Exception e) {
            log.error("Environment JSON 파일 생성 실패", e);
            throw new RuntimeException("Environment JSON 파일 생성 실패", e);
        }
    }

    /**
     * ✅ 업로드 폴더에서 파일명이 전달받은 해시값으로 시작하는 파일을 찾아 Resource로 반환
     *
     * @param hash 클라이언트가 전달한 해시값 (확장자는 포함되지 않음)
     * @return 다운로드 가능한 Resource
     */
    public Resource downloadFileByHash(String hash) {
        FilesMeta meta = uploadMapper.selectOneFileMetaByHash(hash);
        if (meta == null) {
            log.error("해당 해시의 파일 메타 정보가 없습니다: {}", hash);
            throw new RuntimeException("파일을 찾을 수 없습니다.");
        }
        String extension = meta.getExtension();
        String fileName = hash + extension;
        File file = new File(uploadPath, fileName);
        if (!file.exists()) {
            log.error("물리 파일이 존재하지 않습니다: {}", file.getAbsolutePath());
            throw new RuntimeException("파일을 찾을 수 없습니다.");
        }
        log.info("다운로드할 파일 경로: {}", file.getAbsolutePath());
        return new FileSystemResource(file);
    }

}
