package com.aasx.transformer.upload.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import lombok.extern.slf4j.Slf4j;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
public class FileUploadService {

    @Value("${upload.path}")
    private String uploadPath;

    // 파일 이름에서 확장자를 제외한 기본 이름만 반환
    private String getBaseName(String fileName) {
        int lastDotIndex = fileName.lastIndexOf(".");
        if (lastDotIndex == -1) {
            return fileName; // 확장자가 없으면 그대로 파일 이름 반환
        }
        return fileName.substring(0, lastDotIndex);
    }

    // 파일의 확장자만 반환
    private String getFileExtension(String fileName) {
        int lastDotIndex = fileName.lastIndexOf(".");
        if (lastDotIndex == -1) {
            return ""; // 확장자가 없으면 빈 문자열 반환
        }
        return fileName.substring(lastDotIndex); // 확장자 포함
    }

    // 여러 개의 파일 업로드
    public List<String> uploadFiles(MultipartFile[] files) {
        List<String> uploadResults = new ArrayList<>();

        for (MultipartFile file : files) {
            log.info("서비스 - file :::::::::::: {}, Size: {} bytes", file.getOriginalFilename(), file.getSize());

            if (file.isEmpty()) {
                uploadResults.add(file.getOriginalFilename() + ": 파일이 비어 있습니다.");
                continue;
            }

            try {
                String originFileName = file.getOriginalFilename();
                String baseName = getBaseName(originFileName);  // 파일 이름에서 확장자 제외한 이름
                String extension = getFileExtension(originFileName);  // 파일 확장자
                String filePath = uploadPath + "/" + originFileName;

                // 파일이 이미 존재하는 경우, (1), (2) 붙여서 새 파일 경로 생성
                int count = 1;
                File destFile = new File(filePath);

                // 중복되는 파일명이 있을 경우 (1), (2)와 같이 숫자 추가
                while (destFile.exists()) {
                    String newFileName = baseName + " (" + count + ")" + extension;
                    destFile = new File(uploadPath + "/" + newFileName);
                    count++;
                }

                // 파일 저장
                file.transferTo(destFile);
                log.info("파일 업로드 성공: {}", destFile.getAbsolutePath());
                uploadResults.add(originFileName + ": 파일 업로드 성공");

            } catch (IOException e) {
                log.error("파일 업로드 실패: {}", e.getMessage());
                uploadResults.add(file.getOriginalFilename() + ": 파일 업로드 실패 - " + e.getMessage());
            }
        }

        return uploadResults;
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

        log.info("업로드된 파일 목록: {}", fileList);
        return fileList;
    }
}
