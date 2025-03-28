package com.aasx.transformer.download.service;

import org.eclipse.digitaltwin.aas4j.v3.model.Environment;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.util.UUID;

@Service
@Slf4j
public class FileDownloadService {

    @Value("${upload.path}")
    private String uploadPath;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 업데이트된 Environment 객체를 JSON으로 변환 후 임시 파일로 반환
     */
    public Resource downloadEnvironmentAsJson(Environment environment) {
        try {
            String tempFileName = "env-" + UUID.randomUUID() + ".json";
            File tempFile = new File(uploadPath, tempFileName);
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(tempFile, environment);
            log.info("Environment JSON 임시 파일 생성: {}", tempFile.getAbsolutePath());
            return new FileSystemResource(tempFile);
        } catch (Exception e) {
            log.error("Environment JSON 파일 생성 실패", e);
            throw new RuntimeException("Environment JSON 파일 생성 실패", e);
        }
    }

     /**
     * 업로드 폴더에서 파일명이 전달받은 해시값으로 시작하는 파일을 찾아 Resource로 반환합니다.
     *
     * @param hash 클라이언트가 전달한 해시값 (확장자는 포함되지 않음)
     * @return 다운로드 가능한 Resource
     */
    public Resource downloadFileByHash(String hash) {
        File uploadDir = new File(uploadPath);
        if (!uploadDir.exists() || !uploadDir.isDirectory()) {
            log.error("업로드 디렉토리가 존재하지 않습니다: {}", uploadPath);
            throw new RuntimeException("업로드 디렉토리를 찾을 수 없습니다.");
        }

        // 업로드 폴더 내의 파일 중, 파일명이 hash로 시작하는 파일을 검색합니다.
        // 매개변수 
        // - dir: 현재 파일이 속한 디렉토리(File) 객체
        // - name: 현재 검사 중인 파일의 이름(String)
        // listFiles() 메서드: 조건에 맞는 파일들을 배열로 반환
        File[] matchingFiles = uploadDir.listFiles((dir, name) -> name.startsWith(hash));
        if (matchingFiles == null || matchingFiles.length == 0) {
            log.error("해당 해시값으로 시작하는 파일을 찾을 수 없습니다: {}", hash);
            throw new RuntimeException("파일을 찾을 수 없습니다.");
        }

        // 단일 파일 저장
        // listFiles() 메서드 -> 배열에서 파일을 꺼내기 위해 첫 번째 요소(matchingFiles[0])를 선택
        File file = matchingFiles[0];
        String filePath = file.getAbsolutePath();
        log.info("다운로드할 파일 경로: {}", filePath);

        // FileSystemResource: 스트림이 여러 번 요청되어도 문제없이 동작
        return new FileSystemResource(file);
    }
}
