package com.aasx.transformer.deserializer;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.apache.poi.openxml4j.exceptions.InvalidFormatException;
import org.apache.poi.openxml4j.opc.OPCPackage;
import org.apache.poi.openxml4j.opc.PackagePart;
import org.apache.poi.openxml4j.opc.PackagingURIHelper;
import org.eclipse.digitaltwin.aas4j.v3.dataformat.aasx.AASXDeserializer;
import org.eclipse.digitaltwin.aas4j.v3.dataformat.aasx.InMemoryFile;
import org.eclipse.digitaltwin.aas4j.v3.dataformat.aasx.internal.AASXUtils;
import org.eclipse.digitaltwin.aas4j.v3.dataformat.core.SerializationException;
import org.eclipse.digitaltwin.aas4j.v3.dataformat.core.internal.visitor.AssetAdministrationShellElementWalkerVisitor;
import org.eclipse.digitaltwin.aas4j.v3.dataformat.json.JsonSerializer;
import org.eclipse.digitaltwin.aas4j.v3.dataformat.xml.XmlDeserializer;
import org.eclipse.digitaltwin.aas4j.v3.model.Environment;
import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class AASXFileDeserializer {

    // AASX 파일을 읽고 Environment 객체로 변환
    public Environment deserializeAASXFile(InputStream inputStream) {
        log.info("AASX 파일 Deserializer 시작");
        try (InputStream is = inputStream) {
            // XMLDeserializer 인스턴스 생성
            XmlDeserializer xmlDeserializer = new XmlDeserializer();

            // AASXDeserializer 생성 (XMLDeserializer 포함)
            AASXDeserializer deserializer = new AASXDeserializer(xmlDeserializer, is);

            // Environment 객체 읽기
            Environment environment = deserializer.read();
            log.info("▶ .aasx → Environment 변환 완료: assetAdministrationShells={}, submodels={}",
                    environment.getAssetAdministrationShells().size(),
                    environment.getSubmodels().size());
            return environment;
        } catch (Exception e) {
            log.error("AASX Deserialization failed", e);
            throw new RuntimeException(e);
        }
    }

    // environment 객체를 JSON 문자열로 직렬화하여 반환 -> JSON 다운로드 시 활용
    public String serializeEnvironmentToJson(Environment env) {
        JsonSerializer jser = new JsonSerializer();
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try {
            jser.write(baos, env);
        } catch (SerializationException e) {
            log.error("Environment JSON serialization failed", e);
            throw new RuntimeException(e);
        }
        String json = baos.toString(StandardCharsets.UTF_8);

        // modelType 포함 확인
        String snippet = json.length() > 300
                ? json.substring(0, 300) + "…"
                : json;
        log.info("▶ JSON snippet:\n{}", snippet);
        log.info(json.contains("\"modelType\"")
                ? "✔ modelType 포함"
                : "✖ modelType 없음");

        return json;
    }

    // AASX 파일에서 참조된 파일 경로 추출
    public List<String> parseReferencedFilePathsFromAASX(Environment environment) {
        List<String> paths = new ArrayList<>();

        if (environment == null) {
            log.error("환경 객체가 null입니다.");
            return paths;
        }

        // 기본 썸네일 경로 추출
        environment.getAssetAdministrationShells().stream()
                .filter(aas -> aas.getAssetInformation() != null
                        && aas.getAssetInformation().getDefaultThumbnail() != null
                        && aas.getAssetInformation().getDefaultThumbnail().getPath() != null)
                .forEach(aas -> {
                    String thumbPath = aas.getAssetInformation().getDefaultThumbnail().getPath();
                    log.info("Default thumbnail path: {}", thumbPath);
                    paths.add(thumbPath);
                });

        // Submodel 내부의 File 요소 중 value가 채워진 경우의 경로 추출
        new AssetAdministrationShellElementWalkerVisitor() {
            @Override
            public void visit(org.eclipse.digitaltwin.aas4j.v3.model.File file) {
                if (file != null) {
                    // 여기서 전체 정보를 출력
                    log.info("File 요소 전체 정보: idShort={}, value={}", file.getIdShort(), file.getValue());

                    String fileValue = file.getValue();
                    if (fileValue != null && !fileValue.trim().isEmpty()) {
                        log.info("File 요소의 value: {}", fileValue);
                        paths.add(fileValue);
                    } else {
                        log.warn("File 요소의 value가 비어 있음. idShort: {}", file.getIdShort());
                    }
                }
            }
        }.visit(environment);

        log.info("paths: {}", paths);
        return paths;
    }

    // InMemoryFile로 변환
    public List<InMemoryFile> readFiles(OPCPackage aasxRoot, List<String> paths)
            throws InvalidFormatException, IOException {
        List<InMemoryFile> inMemoryFiles = new ArrayList<>();
        for (String path : paths) {
            // 경로를 정제(조정)
            String adjustedPath = AASXUtils.removeFilePartOfURI(path);

            if (adjustedPath == null || adjustedPath.isEmpty()) {
                log.warn("조정된 경로가 비어 있음, 원본 path: {}", path);
                continue;
            }

            PackagePart part = aasxRoot.getPart(PackagingURIHelper.createPartName(adjustedPath));
            try (InputStream stream = part.getInputStream()) {
                byte[] fileData = stream.readAllBytes();
                // 단일 파일 경로 문자열을 인자로 전달하여 InMemoryFile 객체 생성
                InMemoryFile inMemoryFile = new InMemoryFile(fileData, path);
                inMemoryFiles.add(inMemoryFile);
            } catch (Exception e) {
                log.error("InMemoryFile 변환 실패. path={}, adjustedPath={}, 원인: {}", path, adjustedPath, e.getMessage(),
                        e);
            }
        }
        return inMemoryFiles;
    }
}
