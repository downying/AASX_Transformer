package com.aasx.transformer.upload.service;

import java.io.InputStream;

import org.eclipse.digitaltwin.aas4j.v3.dataformat.aasx.AASXDeserializer;
import org.eclipse.digitaltwin.aas4j.v3.dataformat.json.JsonSerializer;
import org.eclipse.digitaltwin.aas4j.v3.model.Environment;
import org.springframework.stereotype.Service;

@Service
public class AASXFileDeserializer {

    // AASX 파일을 읽고 XML을 추출하여 JSON으로 변환
    public String deserializeAASXFile(InputStream aasxFileStream) {
        try {
            // AASX 파일을 읽기
            AASXDeserializer deserializer = new AASXDeserializer(aasxFileStream);
            
            // AASX 파일에서 Environment 객체를 읽음
            Environment environment = deserializer.read();  // 이미 JSON으로 처리됨
            
            // Environment 객체를 JSON으로 변환하여 반환
            return convertEnvironmentToJsonAAS4J(environment);
        } catch (Exception e) {
            e.printStackTrace();
            return "파일 처리 중 오류 발생: " + e.getMessage();
        }
    }

    // Environment 객체를 JSON으로 변환
    private String convertEnvironmentToJsonAAS4J(Environment environment) {
        try {
            // JSON 문자열로 변환
            JsonSerializer  jsonSerializer = new JsonSerializer ();
            return jsonSerializer.write(environment); // JSON 문자열로 변환하여 반환
        } catch (Exception e) {
            e.printStackTrace();
            return "JSON 변환 실패: " + e.getMessage();
        }
    }
}
