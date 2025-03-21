package com.aasx.transformer.upload.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import com.aasx.transformer.upload.service.FileUploadService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;

import org.eclipse.digitaltwin.aas4j.v3.dataformat.aasx.InMemoryFile;
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

    // 업로드된 AASX 파일에서 참조된 파일 경로 조회
    /* @GetMapping("/aasx/referenced-paths")
    public ResponseEntity<Map<String, List<String>>> getReferencedFilePaths() {
        Map<String, List<String>> filePathsMap = fileUploadService.getReferencedFilePaths();
        log.info("컨트롤러 - 파일별 참조된 파일 경로 조회: {}", filePathsMap);
        return ResponseEntity.ok(filePathsMap);
    } */

    // InMemoryFile로 변환
    @GetMapping("/aasx/referenced-inmemoryfiles")
    public ResponseEntity<Map<String, List<InMemoryFile>>> getReferencedInMemoryFiles() {
        Map<String, List<InMemoryFile>> inMemoryFilesMap = fileUploadService.getInMemoryFilesFromReferencedPaths();
        // log.info("컨트롤러 - 파일별 InMemoryFile 목록 조회: {}", inMemoryFilesMap);
        return ResponseEntity.ok(inMemoryFilesMap);
    }

    // SHA-256 해시값 계산 
    @GetMapping("/aasx/sha256-hashes")
    public ResponseEntity<Map<String, List<String>>> getSHA256HashesForInMemoryFiles() {
        Map<String, List<String>> sha256Hashes = fileUploadService.computeSHA256HashesForInMemoryFiles();
        log.info("컨트롤러 - 파일별 SHA-256 해시값 조회: {}", sha256Hashes);
        return ResponseEntity.ok(sha256Hashes);
    }

}
