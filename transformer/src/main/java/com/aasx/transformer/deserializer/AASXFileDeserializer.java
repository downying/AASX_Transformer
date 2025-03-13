package com.aasx.transformer.deserializer;

import org.eclipse.digitaltwin.aas4j.v3.dataformat.aasx.AASXDeserializer;
// import org.eclipse.digitaltwin.aas4j.v3.dataformat.json.JsonSerializer;
import org.eclipse.digitaltwin.aas4j.v3.model.Environment;
import org.eclipse.digitaltwin.aas4j.v3.dataformat.xml.XmlDeserializer;
import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.io.InputStream;

@Slf4j
@Service
public class AASXFileDeserializer {

    // AASX 파일을 읽음
    public Environment   deserializeAASXFile(InputStream inputStream) {
        Environment environment = null;
        // String jsonResult = null;
        try {
            log.info("AASX 파일 Deserializer 시작");

            // XMLDeserializer 인스턴스 생성
            XmlDeserializer xmlDeserializer = new XmlDeserializer();

            // AASXDeserializer 생성 (XMLDeserializer 포함)
            AASXDeserializer deserializer = new AASXDeserializer(xmlDeserializer, inputStream);

            // Environment 객체 읽기
            environment = deserializer.read();

            // Environment 객체를 JSON으로 변환
            // jsonResult = convertEnvironmentToJson(environment);

            // 줄 바꿈 문자를 운영체제에 맞게 처리
           /*  if (jsonResult != null) {
                jsonResult = jsonResult.replaceAll("\\R", "");
            } */

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
}
