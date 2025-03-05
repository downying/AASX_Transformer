package com.aasx.transformer.deserializer;

import org.eclipse.digitaltwin.aas4j.v3.dataformat.aasx.AASXDeserializer;
import org.eclipse.digitaltwin.aas4j.v3.dataformat.json.JsonSerializer;
import org.eclipse.digitaltwin.aas4j.v3.model.Environment;
import org.springframework.stereotype.Service;
import lombok.extern.slf4j.Slf4j;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
public class AASXFileDeserializer {

    // AASX 파일을 읽고 XML을 JSON으로 변환
    public List<String> deserializeAASXFile(InputStream aasxFileStream) {
        List<String> jsonResults = new ArrayList<>();
        try {
            // AASX 파일에서 Environment 객체를 읽음
            log.info("AASX 파일 Deserializer 시작");
            AASXDeserializer deserializer = new AASXDeserializer(aasxFileStream);

            // AASX 파일에서 하나씩 객체를 읽음
            while (true) {
                try {
                    log.info("AASX 파일 크기: {}", aasxFileStream.available());

                    // Environment 객체를 읽음
                    Environment environment = deserializer.read();  // 하나씩 처리
                    if (environment == null) {
                        log.info("환경 객체가 null입니다.");
                        break;
                    }

                    log.info("environment: {}", environment);

                    // Environment 객체를 JSON으로 변환하여 리스트에 추가
                    String json = convertEnvironmentToJson(environment);
                    log.info("json 변환: {}", json);
                    jsonResults.add(json);

                } catch (Exception e) {
                    log.info("AASX 파일에서 더 이상 읽을 수 있는 객체가 없습니다.");
                    break;
                }
            }

            // 변환된 JSON 결과 리스트 반환
            log.info("AASX 파일 JSON으로 변환 완료");
        } catch (Exception e) {
            log.error("AASX 파일을 변환할 수 없습니다: {}", e.getMessage());
            jsonResults.add("AASX 파일을 변환할 수 없습니다: " + e.getMessage());
        }
        return jsonResults;
    }

    // Environment 객체를 JSON으로 변환
    private String convertEnvironmentToJson(Environment environment) {
        try {
            JsonSerializer jsonSerializer = new JsonSerializer();
            String json = jsonSerializer.write(environment);  // JSON 문자열로 변환하여 반환
            log.info("convertEnvironmentToJson - json: {}", json);

            return json;
        } catch (Exception e) {
            log.error("JSON 변환 실패: {}", e.getMessage());
            return "JSON 변환 실패: " + e.getMessage();
        }
    }
}
