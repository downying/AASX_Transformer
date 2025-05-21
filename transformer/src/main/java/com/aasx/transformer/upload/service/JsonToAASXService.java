package com.aasx.transformer.upload.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.digitaltwin.aas4j.v3.dataformat.json.JsonDeserializer;
import org.eclipse.digitaltwin.aas4j.v3.model.AnnotatedRelationshipElement;
import org.eclipse.digitaltwin.aas4j.v3.model.Blob;
import org.eclipse.digitaltwin.aas4j.v3.model.DataSpecificationContent;
import org.eclipse.digitaltwin.aas4j.v3.model.DataSpecificationIec61360;
import org.eclipse.digitaltwin.aas4j.v3.model.Environment;
import org.eclipse.digitaltwin.aas4j.v3.model.File;
import org.eclipse.digitaltwin.aas4j.v3.model.SubmodelElementCollection;
import org.eclipse.digitaltwin.aas4j.v3.model.impl.DefaultAnnotatedRelationshipElement;
import org.eclipse.digitaltwin.aas4j.v3.model.impl.DefaultBlob;
import org.eclipse.digitaltwin.aas4j.v3.model.impl.DefaultFile;
import org.eclipse.digitaltwin.aas4j.v3.model.impl.DefaultProperty;
import org.eclipse.digitaltwin.aas4j.v3.model.impl.DefaultSubmodelElementCollection;
import org.eclipse.digitaltwin.aas4j.v3.model.Property;
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
     * 여러 JSON 파일 업로드 및 Environment 변환
     */
     public List<Environment> uploadJsonFiles(MultipartFile[] jsonFiles) {
        List<Environment> results = new ArrayList<>();
        for (MultipartFile file : jsonFiles) {
            try {
                Path tmp = Files.createTempFile(Path.of(System.getProperty("java.io.tmpdir")), "aasx-", ".json");
                Files.copy(file.getInputStream(), tmp, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                results.add(parseEnvironment(tmp));
            } catch (Exception ex) {
                log.error("JSON 파일 처리 오류 ({}): {}", file.getOriginalFilename(), ex.getMessage());
            }
        }
        return results;
    }

    /**
     * 실제 JSON → Environment 역직렬라이즈
     * useImplementation 으로 인터페이스별 구현체를 지정
     */
    private Environment parseEnvironment(Path jsonPath) {
        try {
            String raw = Files.readString(jsonPath);

            // ─── 여기에 로그를 추가 ───
            String snippet = raw.length() > 500
                ? raw.substring(0, 500) + "…(생략)"
                : raw;
            log.info("▶ parseEnvironment() — raw snippet:\n{}", snippet);
            log.info("   Contains modelType? {}", raw.contains("\"modelType\""));

            JsonDeserializer deserializer = new JsonDeserializer();

            JsonDeserializer des = new JsonDeserializer();
            des.useImplementation(DataSpecificationContent.class, DataSpecificationIec61360.class);
            des.useImplementation(SubmodelElementCollection.class, DefaultSubmodelElementCollection.class);
            des.useImplementation(AnnotatedRelationshipElement.class, DefaultAnnotatedRelationshipElement.class);
            des.useImplementation(Blob.class, DefaultBlob.class);
            des.useImplementation(File.class, DefaultFile.class);
            des.useImplementation(Property.class, DefaultProperty.class);

            Environment env = des.read(raw, Environment.class);
            log.info("JSON 파싱 성공: AAS={} / Submodels={}",
                     env.getAssetAdministrationShells().size(),
                     env.getSubmodels().size());
            return env;

        } catch (IOException ioe) {
            log.error("JSON 파일 읽기 실패", ioe);
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "JSON 파일 읽기 실패: " + ioe.getMessage(), ioe
            );
        } catch (Exception e) {
            log.error("JSON 파싱 실패", e);
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "JSON 파싱 실패: " + e.getMessage(), e
            );
        }
    }

}
