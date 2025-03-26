package com.aasx.transformer.download.controller;

import com.aasx.transformer.download.service.FileDownloadService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;

@RestController
@RequestMapping("/api/transformer")
public class FileDownloadController {

    @Autowired
    private FileDownloadService fileDownloadService;

    /**
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
