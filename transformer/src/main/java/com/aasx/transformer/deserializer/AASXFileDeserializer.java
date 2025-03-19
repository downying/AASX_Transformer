package com.aasx.transformer.deserializer;

import org.apache.poi.openxml4j.exceptions.InvalidFormatException;
import org.apache.poi.openxml4j.opc.OPCPackage;
import org.apache.poi.openxml4j.opc.PackagePart;
import org.apache.poi.openxml4j.opc.PackagingURIHelper;
import org.eclipse.digitaltwin.aas4j.v3.dataformat.aasx.AASXDeserializer;
import org.eclipse.digitaltwin.aas4j.v3.dataformat.aasx.InMemoryFile;
import org.eclipse.digitaltwin.aas4j.v3.dataformat.aasx.internal.AASXUtils;
import org.eclipse.digitaltwin.aas4j.v3.dataformat.core.internal.visitor.AssetAdministrationShellElementWalkerVisitor;
// import org.eclipse.digitaltwin.aas4j.v3.dataformat.json.JsonSerializer;
import org.eclipse.digitaltwin.aas4j.v3.model.Environment;
import org.eclipse.digitaltwin.aas4j.v3.model.File;
import org.eclipse.digitaltwin.aas4j.v3.dataformat.xml.XmlDeserializer;
import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
public class AASXFileDeserializer {

    // AASX 파일을 읽음
    public Environment   deserializeAASXFile(InputStream inputStream) {
        Environment environment = null;

        try {
            log.info("AASX 파일 Deserializer 시작");

            // XMLDeserializer 인스턴스 생성
            XmlDeserializer xmlDeserializer = new XmlDeserializer();

            // AASXDeserializer 생성 (XMLDeserializer 포함)
            AASXDeserializer deserializer = new AASXDeserializer(xmlDeserializer, inputStream);

            // Environment 객체 읽기
            environment = deserializer.read();
            // log.info("deserializeAASXFile에서 반환된 environment: {}", environment);

        } catch (Exception e) {
            log.error("AASX 파일 변환 실패: {}", e.getMessage());
        } finally {
            try {
                if (inputStream != null) {
                    inputStream.close(); // InputStream 종료
                    log.info("InputStream이 성공적으로 종료되었습니다.");
                }
            } catch (IOException e) {
                log.error("InputStream 종료 중 오류 발생: {}", e.getMessage());
            }
        }

        // JSON 결과를 JsonResults 객체에 담아서 반환
        // return new JsonResults(jsonResult);

        return environment;
    }

    // Environment 객체를 JSON으로 변환
    /* private String convertEnvironmentToJson(Environment environment) {
        try {
            JsonSerializer jsonSerializer = new JsonSerializer();
            return jsonSerializer.write(environment);  // JSON 문자열로 변환하여 반환
        } catch (Exception e) {
            log.error("JSON 변환 실패: {}", e.getMessage());
            return "JSON 변환 실패: " + e.getMessage();
        }
    } */

    // AASX 파일에서 참조된 파일 경로 추출
    public List<String> parseReferencedFilePathsFromAASX(Environment environment) {
        List<String> paths = new ArrayList<>();

        if (environment == null) {
            log.error("환경 객체가 null입니다.");
            return paths; 
        }

        // log.info("environment {} ", environment);

        // 기본 썸네일 경로 추출
        environment.getAssetAdministrationShells().stream()
            .filter(aas -> aas.getAssetInformation() != null
                && aas.getAssetInformation().getDefaultThumbnail() != null
                && aas.getAssetInformation().getDefaultThumbnail().getPath() != null) 
            .forEach(aas -> paths.add(aas.getAssetInformation().getDefaultThumbnail().getPath()));

        // 서브모델 내부의 File 객체에서 파일 경로 추출
        new AssetAdministrationShellElementWalkerVisitor() {
            @Override
            public void visit(File file) {
                if (file != null && file.getValue() != null) {
                    paths.add(file.getValue());
                }
            }
        }.visit(environment);
        log.info("paths: {}", paths);

        return paths;
    }

    // InMemoryFile로 변환
    /* public InMemoryFile readFile(OPCPackage aasxRoot, String filePath) throws InvalidFormatException, IOException {
        PackagePart part = aasxRoot.getPart(PackagingURIHelper.createPartName(AASXUtils.removeFilePartOfURI(filePath)));
        InputStream stream = part.getInputStream();
        return new InMemoryFile(stream.readAllBytes(), filePath);
    } */
}