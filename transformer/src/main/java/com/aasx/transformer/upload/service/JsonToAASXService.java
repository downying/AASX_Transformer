// JsonToAASXService.java
package com.aasx.transformer.upload.service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.digitaltwin.aas4j.v3.dataformat.core.DeserializationException;
import org.eclipse.digitaltwin.aas4j.v3.dataformat.json.JsonDeserializer;
import org.eclipse.digitaltwin.aas4j.v3.model.AnnotatedRelationshipElement;
import org.eclipse.digitaltwin.aas4j.v3.model.Blob;
import org.eclipse.digitaltwin.aas4j.v3.model.Capability;
import org.eclipse.digitaltwin.aas4j.v3.model.DataSpecificationContent;
import org.eclipse.digitaltwin.aas4j.v3.model.DataSpecificationIec61360;
import org.eclipse.digitaltwin.aas4j.v3.model.Environment;
import org.eclipse.digitaltwin.aas4j.v3.model.File;
import org.eclipse.digitaltwin.aas4j.v3.model.MultiLanguageProperty;
import org.eclipse.digitaltwin.aas4j.v3.model.Operation;
import org.eclipse.digitaltwin.aas4j.v3.model.Property;
import org.eclipse.digitaltwin.aas4j.v3.model.Range;
import org.eclipse.digitaltwin.aas4j.v3.model.ReferenceElement;
import org.eclipse.digitaltwin.aas4j.v3.model.RelationshipElement;
import org.eclipse.digitaltwin.aas4j.v3.model.SubmodelElementCollection;
import org.eclipse.digitaltwin.aas4j.v3.model.impl.DefaultAnnotatedRelationshipElement;
import org.eclipse.digitaltwin.aas4j.v3.model.impl.DefaultBlob;
import org.eclipse.digitaltwin.aas4j.v3.model.impl.DefaultCapability;
import org.eclipse.digitaltwin.aas4j.v3.model.impl.DefaultFile;
import org.eclipse.digitaltwin.aas4j.v3.model.impl.DefaultMultiLanguageProperty;
import org.eclipse.digitaltwin.aas4j.v3.model.impl.DefaultOperation;
import org.eclipse.digitaltwin.aas4j.v3.model.impl.DefaultProperty;
import org.eclipse.digitaltwin.aas4j.v3.model.impl.DefaultRange;
import org.eclipse.digitaltwin.aas4j.v3.model.impl.DefaultReferenceElement;
import org.eclipse.digitaltwin.aas4j.v3.model.impl.DefaultRelationshipElement;
import org.eclipse.digitaltwin.aas4j.v3.model.impl.DefaultSubmodelElementCollection;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class JsonToAASXService {

    @Value("${upload.temp-path}")
    private String tempPath;

    /**
     * 멀티 JSON 파일 업로드 및 파싱
     */
    public List<Environment> uploadJsonFiles(MultipartFile[] jsonFiles) {
        List<Environment> results = new ArrayList<>();
        for (MultipartFile jsonFile : jsonFiles) {
            // 1) 파일 저장
            String original = saveJson(jsonFile);

            // 2) 저장된 경로로부터 다시 읽어와 파싱
            Path dest = Paths.get(tempPath).resolve(original);
            results.add(parseEnvironment(dest));
        }
        return results;
    }

    /** 단일 JSON 파일 저장 (확장자 검사 및 덮어쓰기) */
    private String saveJson(MultipartFile jsonFile) {
        String original = Paths.get(jsonFile.getOriginalFilename())
                .getFileName().toString();
        if (!original.toLowerCase().endsWith(".json")) {
            original += ".json";
        }
        Path uploadDir = Paths.get(tempPath);
        Path dest = uploadDir.resolve(original);

        try {
            if (Files.notExists(uploadDir)) {
                Files.createDirectories(uploadDir);
                log.info("디렉토리 생성: {}", uploadDir);
            }
            try (InputStream in = jsonFile.getInputStream()) {
                Files.copy(in, dest, StandardCopyOption.REPLACE_EXISTING);
            }
            log.info("JSON 파일 저장 완료: {}", dest);
            return original;

        } catch (IOException e) {
            log.error("파일 저장/읽기 오류", e);
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "JSON 저장 실패: " + e.getMessage(), e);
        }
    }

    /** 저장된 JSON 파일을 AAS4J Environment 로 변환 */
    private Environment parseEnvironment(Path jsonPath) {
        try {
            byte[] data = Files.readAllBytes(jsonPath);

            // 1) JsonDeserializer 생성
            JsonDeserializer deserializer = new JsonDeserializer();

            // 2) DataSpecification 매핑
            deserializer.useImplementation(
                    DataSpecificationContent.class,
                    DataSpecificationIec61360.class);

            // 3) SubmodelElementCollection 매핑
            deserializer.useImplementation(
                    SubmodelElementCollection.class,
                    DefaultSubmodelElementCollection.class);

            // 4) Blob / File 매핑
            deserializer.useImplementation(Blob.class, DefaultBlob.class);
            deserializer.useImplementation(File.class, DefaultFile.class);

            // 5) 기본 DataElement 매핑
            deserializer.useImplementation(Property.class, DefaultProperty.class);
            deserializer.useImplementation(MultiLanguageProperty.class, DefaultMultiLanguageProperty.class);
            deserializer.useImplementation(Range.class, DefaultRange.class);

            // 6) Relationships 매핑
            deserializer.useImplementation(RelationshipElement.class, DefaultRelationshipElement.class);
            deserializer.useImplementation(AnnotatedRelationshipElement.class,
                    DefaultAnnotatedRelationshipElement.class);
            deserializer.useImplementation(ReferenceElement.class, DefaultReferenceElement.class);

            // 7) Operation / Capability 매핑
            deserializer.useImplementation(Operation.class, DefaultOperation.class);
            deserializer.useImplementation(Capability.class, DefaultCapability.class);

            // 8) 스트림으로부터 파싱
            try (InputStream in = new ByteArrayInputStream(data)) {
                Environment env = deserializer.read(in, Environment.class);
                log.info("[AAS4J] JSON 파싱 성공: AAS={} Submodels={} ConceptDescriptions={}",
                        env.getAssetAdministrationShells().size(),
                        env.getSubmodels().size(),
                        env.getConceptDescriptions().size());
                return env;
            }

        } catch (DeserializationException dex) {
            log.error("[AAS4J] 파싱 중 예외: {}", dex.getCause().getMessage(), dex);
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "JSON 파싱 실패: " + dex.getCause().getMessage(), dex);

        } catch (Exception e) {
            log.error("AAS4J JSON 파싱 실패: {}", e.getMessage(), e);
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "JSON 파싱 실패: " + e.getMessage(), e);
        }
    }
}
