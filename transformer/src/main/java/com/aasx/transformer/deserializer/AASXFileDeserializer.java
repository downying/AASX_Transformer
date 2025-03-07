package com.aasx.transformer.deserializer;

import org.eclipse.digitaltwin.aas4j.v3.dataformat.aasx.AASXDeserializer;
import org.eclipse.digitaltwin.aas4j.v3.dataformat.json.JsonSerializer;
import org.eclipse.digitaltwin.aas4j.v3.model.Environment;
import org.eclipse.digitaltwin.aas4j.v3.dataformat.xml.XmlDeserializer;
import org.springframework.stereotype.Service;
import lombok.extern.slf4j.Slf4j;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
public class AASXFileDeserializer {

    // AASX 파일을 읽고 XML을 JSON으로 변환
    public List<String> deserializeAASXFile(InputStream inputStream) {
        List<String> jsonResults = new ArrayList<>();
        try {
            log.info("AASX 파일 Deserializer 시작");

            // XMLDeserializer 인스턴스 생성
            XmlDeserializer xmlDeserializer = new XmlDeserializer();

            // AASXDeserializer 생성 (XMLDeserializer 포함)
            AASXDeserializer deserializer = new AASXDeserializer(xmlDeserializer, inputStream);

            // 여러 개의 Environment 객체를 읽기 위한 반복문
            Environment environment;
            while ((environment = deserializer.read()) != null) {
                try {
                    // log.info("Environment 객체 읽기 성공")

                    // Environment 객체를 JSON으로 변환
                    String json = convertEnvironmentToJson(environment);
                    jsonResults.add(json);
                } catch (Exception e) {
                    log.error("환경 객체 처리 중 오류 발생: {}", e.getMessage());
                    break; // 예외 발생 시 루프 종료
                }
            }

            // JSON 배열 형식으로 출력
            log.info("변환 완료 - 변환된 JSON 객체: {}", jsonResults);

        } catch (Exception e) {
            log.error("AASX 파일 변환 실패: {}", e.getMessage());
            jsonResults.add("AASX 파일 변환 실패: " + e.getMessage());
        }
        return jsonResults;
    }

    // Environment 객체를 JSON으로 변환
    private String convertEnvironmentToJson(Environment environment) {
        try {
            JsonSerializer jsonSerializer = new JsonSerializer();
            String json = jsonSerializer.write(environment);  // JSON 문자열로 변환하여 반환
            // log.info("convertEnvironmentToJson - json: {}", json);
            return json;
        } catch (Exception e) {
            log.error("JSON 변환 실패: {}", e.getMessage());
            return "JSON 변환 실패: " + e.getMessage();
        }
    }
}

