package com.aasx.transformer.deserializer;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import org.eclipse.digitaltwin.aas4j.v3.dataformat.aasx.InMemoryFile;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class SHA256Hash {
    public static String computeSHA256Hash(InMemoryFile file) {
        try {
            // getFileContent(): InMemoryFile 클래스가 파일의 바이트 배열을 제공하는 메서드
            // 파일의 모든 데이터를 byte[]로 읽어옴
            byte[] fileContent = file.getFileContent();
            
            // MessageDigest: 다양한 해시 알고리즘(MD5, SHA-1, SHA-256 등)을 지원
            // SHA-256 알고리즘을 사용하는 인스턴스를 생성
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            // digest(): 전달된 바이트 배열 (fileContent)을 해싱 처리하여 해시 결과를 바이트 배열(hashBytes)로 반환
            byte[] hashBytes = digest.digest(fileContent);
            log.info("hashBytes: {}", hashBytes);
            
            // 바이트 배열을 16진수 문자열로 변환
            StringBuilder hexString = new StringBuilder();
            for (byte b : hashBytes) {
                // 비트 마스킹 - 0xff & b: 바이트 값을 0~255 범위의 정수로 변환
                int num = 0xff & b;
                // log.info("num: {}", num);

                // Integer.toHexString(): 해당 정수를 16진수 문자열로 변환
                String hex = Integer.toHexString(num);
                // log.info("hex: ", hex);

                // 변환된 16진수 문자열의 길이가 1이면 앞에 0을 추가
                // 모든 바이트를 항상 2자리 16진수로 표현
                if (hex.length() == 1) {
                    hexString.append('0');
                }

                hexString.append(hex);
            }
            
            log.info("hexString 결과: {}", hexString);
            return hexString.toString();

        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 알고리즘을 사용할 수 없습니다.", e);
        }
    }
}


/* 
✅ Java에서 byte 타입 :  -128 ~ 127
✅ byte b2 = 255; : 직접 사용❌
➡ (byte)255처럼 명시적 형변환 필요

(예시)
byte b1 = 7;            // 십진수 7
byte b2 = (byte)255;    // 십진수 255

String hex1 = Integer.toHexString(0xff & b1);  // "7"
if (hex1.length() == 1) {
    hex1 = "0" + hex1;   // "07"로 변경
}

String hex2 = Integer.toHexString(0xff & b2);  // "ff" 

System.out.println("b1: " + hex1); // 출력: b1: 07
System.out.println("b2: " + hex2); // 출력: b2: ff

 */
