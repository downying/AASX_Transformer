package com.aasx.transformer.download.controller;

import com.aasx.transformer.download.service.FileDownloadService;
import com.aasx.transformer.upload.service.FileUploadService;

import lombok.extern.slf4j.Slf4j;

import org.eclipse.digitaltwin.aas4j.v3.model.Environment;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/transformer")
public class FileDownloadController {

    @Autowired
    private FileUploadService fileUploadService;

    @Autowired
    private FileDownloadService fileDownloadService;

    /**
     * ✅ 특정 파일의 업데이트된 Environment JSON 다운로드
     * 예시 URL: /api/transformer/download/environment/{fileName}
     */
    @GetMapping("/download/environment/{fileName}")
    public ResponseEntity<Resource> downloadEnvironment(@PathVariable("fileName") String fileName) {
        // URL 경로에서 전달받은 fileName 값을 로그로 기록
        log.info("다운로드 요청된 파일 이름: {}", fileName);

        Map<String, Environment> updatedEnvironmentMap = fileUploadService.computeSHA256HashesForInMemoryFiles();
        if (!updatedEnvironmentMap.containsKey(fileName)) {
            log.warn("요청된 파일 이름 '{}' 에 해당하는 환경 정보가 존재하지 않습니다.", fileName);
            return ResponseEntity.notFound().build();
        }
        Environment updatedEnvironment = updatedEnvironmentMap.get(fileName);
        Resource resource = fileDownloadService.downloadEnvironmentAsJson(updatedEnvironment, fileName);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + resource.getFilename() + "\"")
                .contentType(MediaType.APPLICATION_JSON)
                .body(resource);
    }

    /**
     * ✅ 첨부파일 다운로드
     * @param hash 다운로드할 파일의 SHA-256 해시값 (저장된 파일명)
     * @return 파일을 포함한 ResponseEntity
     */
    @GetMapping("/download/{hash}")
    public ResponseEntity<Resource> downloadFile(@PathVariable("hash") String hash) {
        Resource resource = fileDownloadService.downloadFileByHash(hash);

        // 파일명은 현재 해시값으로 사용
        // Phase 2에서 파일 확장자, 콘텐츠 타입 등의 정보 보완 필요
        String fileName = hash;

        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileName + "\"");

        try {
            return ResponseEntity.ok()
                    .headers(headers)
                    .contentLength(resource.contentLength())
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .body(resource);
        } catch (IOException e) {
            throw new RuntimeException("파일 다운로드 처리 중 오류가 발생했습니다.", e);
        }
    }
}
