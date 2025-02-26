package com.aasx.transformer.upload.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import lombok.extern.slf4j.Slf4j;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
public class FileUploadService {

    // application.properties에 설정한 업로드 경로를 가져옴
    @Value("${upload.path}")
    private String uploadPath;

    // 파일 업로드 로직
    public String uploadFile(MultipartFile file) {
        log.info("file :::::::::::: {}, Size: {} bytes", file.getOriginalFilename(), file.getSize());

        if (file.isEmpty()) {
            return "파일이 비어 있습니다.";
        }

        try {
            // 파일의 원본 파일 이름
            String originFileName = file.getOriginalFilename();
            String filePath = uploadPath + "/" + originFileName;

            // 파일이 이미 존재하면 업로드하지 않음
            File destFile = new File(filePath);
            if (destFile.exists()) {
                log.info("이미 같은 이름의 파일이 존재합니다: {}", originFileName);
                return "이미 같은 이름의 파일이 존재합니다.";
            }

            // 파일 저장
            file.transferTo(destFile); // 파일을 지정된 위치로 업로드

            log.info("파일 업로드 성공: {}", filePath);
            return "파일 업로드 성공";
        } catch (IOException e) {
            log.error("파일 업로드 실패: {}", e.getMessage());
            return "파일 업로드 실패: " + e.getMessage();
        }
    }

    // 업로드된 파일 목록 조회
    public List<String> getUploadedFiles() {
        File folder = new File(uploadPath);
        if (!folder.exists()) {
            return List.of();
        }

        List<String> fileList = List.of(folder.list())
                .stream()
                .sorted()
                .collect(Collectors.toList());

        // 파일 목록 조회 후 로그 출력
        log.info("업로드된 파일 목록: {}", fileList);

        return fileList;
    }
}
