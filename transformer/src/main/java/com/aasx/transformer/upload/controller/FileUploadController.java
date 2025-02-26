package com.aasx.transformer.upload.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import com.aasx.transformer.upload.service.FileUploadService;

import lombok.extern.slf4j.Slf4j;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/transformer")
// @CrossOrigin(origins = "*") // CORS 허용
public class FileUploadController {

    private final FileUploadService fileUploadService;

    public FileUploadController(FileUploadService fileUploadService) {
        this.fileUploadService = fileUploadService;
    }

    // 파일 업로드
    @PostMapping("/aasx")
    public ResponseEntity<String> uploadFile(@RequestParam(value = "file", required = false) MultipartFile file) {
        log.info("컨트롤러 - 요청 파일 : {}", file); // 요청 로그 확인

        if (file == null || file.isEmpty()) {
            log.info("파일이 없습니다!");
            return ResponseEntity.badRequest().body("No file uploaded");
        }

        log.info("업로드된 파일 : {}", file.getOriginalFilename());
        String message = fileUploadService.uploadFile(file);
        return ResponseEntity.ok(message);
    }

    // 업로드된 파일 목록 조회
    @GetMapping("/files")
    public ResponseEntity<List<String>> listUploadedFiles() {
        List<String> files = fileUploadService.getUploadedFiles();
        return ResponseEntity.ok(files);
    }
}
