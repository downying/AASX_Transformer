package com.aasx.transformer.download.service;

import org.eclipse.digitaltwin.aas4j.v3.model.Environment;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import com.aasx.transformer.upload.dto.FilesMeta;
import com.aasx.transformer.upload.mapper.UploadMapper;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.extern.slf4j.Slf4j;

import java.io.File;

@Service
@Slf4j
public class FileDownloadService {

    @Autowired
    private UploadMapper uploadMapper;

    @Value("${upload.path}")
    private String uploadPath;

    private final ObjectMapper objectMapper = new ObjectMapper();

    // ✅ 업데이트된 Environment 객체를 JSON으로 변환 후 임시 파일로 반환
    public Resource downloadEnvironmentAsJson(Environment environment, String originalFileName) {
        log.info("downloadEnvironmentAsJson 호출 - originalFileName: {}", originalFileName);
        try {
            String baseName = originalFileName;
            int dotIndex = originalFileName.lastIndexOf(".");
            if (dotIndex > 0) {
                baseName = originalFileName.substring(0, dotIndex);
            }
            String tempFileName = baseName + ".json";
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
     * ✅ 업로드 폴더에서 파일명이 전달받은 해시값으로 시작하는 파일을 찾아 Resource로 반환
     *
     * @param hash 클라이언트가 전달한 해시값 (확장자는 포함되지 않음)
     * @return 다운로드 가능한 Resource
     */
    public Resource downloadFileByHash(String hash) {
        FilesMeta meta = uploadMapper.selectOneFileMetaByHash(hash);
        if (meta == null) {
            log.error("해당 해시의 파일 메타 정보가 없습니다: {}", hash);
            throw new RuntimeException("파일을 찾을 수 없습니다.");
        }
        String extension = meta.getExtension();
        String fileName = hash + extension;
        File file = new File(uploadPath, fileName);
        if (!file.exists()) {
            log.error("물리 파일이 존재하지 않습니다: {}", file.getAbsolutePath());
            throw new RuntimeException("파일을 찾을 수 없습니다.");
        }
        log.info("다운로드할 파일 경로: {}", file.getAbsolutePath());
        return new FileSystemResource(file);
    }

}
