package com.aasx.transformer.deserializer;

import org.apache.commons.codec.digest.DigestUtils;
import org.eclipse.digitaltwin.aas4j.v3.dataformat.aasx.InMemoryFile;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class SHA256HashApache {

    public static String computeSHA256Hash(InMemoryFile file) {
        try {
            byte[] fileContent = file.getFileContent();

            // DigestUtils를 이용해 SHA-256 해시를 16진수 문자열로 바로 계산
            String sha256Hex = DigestUtils.sha256Hex(fileContent);

            log.info("SHA-256 해시 결과 (DigestUtils): {}", sha256Hex);

            return sha256Hex;
        } catch (Exception e) {
            throw new RuntimeException("SHA-256 해시 계산 중 오류 발생", e);
        }
    }
}
