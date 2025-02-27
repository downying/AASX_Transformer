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
public class FileUploadController {

    private final FileUploadService fileUploadService;

    public FileUploadController(FileUploadService fileUploadService) {
        this.fileUploadService = fileUploadService;
    }

    // 여러 개의 파일 업로드
    @PostMapping("/aasx")
    public ResponseEntity<List<String>> uploadFiles(@RequestParam("files") MultipartFile[] files) {
        log.info("컨트롤러 - 요청된 파일 개수: {}", files.length);
        List<String> uploadResults = fileUploadService.uploadFiles(files);
        return ResponseEntity.ok(uploadResults);
    }

    // 업로드된 파일 목록 조회
    @GetMapping("/files")
    public ResponseEntity<List<String>> listUploadedFiles() {
        List<String> files = fileUploadService.getUploadedFiles();
        return ResponseEntity.ok(files);
    }
}
