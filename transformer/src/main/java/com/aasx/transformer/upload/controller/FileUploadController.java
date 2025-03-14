package com.aasx.transformer.upload.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import com.aasx.transformer.upload.service.FileUploadService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import java.util.List;

import org.eclipse.digitaltwin.aas4j.v3.model.Environment;

@Slf4j
@RestController
@RequestMapping("/api/transformer")
public class FileUploadController {

    @Autowired
    private FileUploadService fileUploadService; 

    // 여러 개의 파일 업로드
    @PostMapping("/aasx")
    public ResponseEntity<List<Environment>> uploadFiles(@RequestParam("files") MultipartFile[] files) {
        log.info("컨트롤러 - 요청된 파일 개수: {}", files.length);
        List<Environment> uploadResults = fileUploadService.uploadFiles(files);
        return ResponseEntity.ok(uploadResults);
    }

    // 업로드된 파일 이름 조회
    @GetMapping("/uploadedFileNames")
    public ResponseEntity<List<String>> listUploadedFiles() {
        List<String> uploadedFileNames = fileUploadService.getUploadedFiles();
        log.info("컨트롤러 - 업로드된 파일 이름 조회: {}", uploadedFileNames);
        return ResponseEntity.ok(uploadedFileNames);
    }
}

