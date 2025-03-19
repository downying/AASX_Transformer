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

    // 최근 업로드된 AASX 파일에서 참조된 파일 경로 조회
    @PostMapping("/aasx/referenced-paths")
    public ResponseEntity<List<String>> extractFilesFromAASX(@RequestParam("file") MultipartFile file) {
        try (InputStream inputStream = file.getInputStream()) {
            List<String> filePaths = fileUploadService.extractFilePathsFromAASX(inputStream);
            return ResponseEntity.ok(filePaths);
        } catch (IOException e) {
            return ResponseEntity.status(500).body(null);  // 예외 발생 시 500 에러 반환
        }
    }

    // AASX 파일에서 특정 파일을 추출하여 반환
  /*   @PostMapping("/aasx/extract-file")
    public ResponseEntity<byte[]> extractFileFromAASX(@RequestParam("file") MultipartFile file,
                                                      @RequestParam("filePath") String filePath) {
        log.info("AASX 파일에서 특정 파일 추출 요청: {} (경로: {})", file.getOriginalFilename(), filePath);
        InMemoryFile extractedFile = fileUploadService.getFileFromAASX(file, filePath);

        if (extractedFile != null) {
            return ResponseEntity.ok()
                .header("Content-Disposition", "attachment; filename=\"" + file.getOriginalFilename() + "\"")
                .body(extractedFile.getFileContent());
        }

        return ResponseEntity.notFound().build();
    } */
}
