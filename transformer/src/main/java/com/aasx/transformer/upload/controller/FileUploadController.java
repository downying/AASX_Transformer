package com.aasx.transformer.upload.controller;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.core.io.Resource;

import com.aasx.transformer.upload.service.FileUploadService;
import com.aasx.transformer.upload.service.JsonToAASXService;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.InputStreamResource;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;

import org.eclipse.digitaltwin.aas4j.v3.dataformat.core.DeserializationException;
import org.eclipse.digitaltwin.aas4j.v3.dataformat.core.SerializationException;
import org.eclipse.digitaltwin.aas4j.v3.model.Environment;

@Slf4j
@RestController
@RequestMapping("/api/transformer")
public class FileUploadController {

    @Autowired
    private FileUploadService fileUploadService;

    @Autowired
    private JsonToAASXService jsonToAasxService;

    // ✅ 여러 개의 파일 업로드
    @PostMapping("/aasx")
    public ResponseEntity<List<Environment>> uploadFiles(@RequestParam("files") MultipartFile[] files) {
        log.info("컨트롤러 - 요청된 파일 개수: {}", files.length);
        // 1) AASX 파일 저장 + Environment 변환
        List<Environment> uploadResults = fileUploadService.uploadFiles(files);

        // 2) 방금 업로드된 InMemoryFile 들에 대해 해시 계산 → DB 파일 메타 등록
        fileUploadService.computeSHA256HashesForInMemoryFiles();
        return ResponseEntity.ok(uploadResults);
    }

    // ✅ 업로드된 파일 이름 조회
    @GetMapping("/uploadedFileNames")
    public ResponseEntity<List<String>> listUploadedFiles() {
        List<String> uploadedFileNames = fileUploadService.getUploadedFiles();
        log.info("컨트롤러 - 업로드된 파일 이름 조회: {}", uploadedFileNames);
        return ResponseEntity.ok(uploadedFileNames);
    }

    // ✅ JSON 파일을 받아서 AASX 패키지로 변환
    @PostMapping("/json")
    public ResponseEntity<List<Environment>> uploadJson(
            @RequestParam("files") MultipartFile[] files) {

        log.info("컨트롤러 - JSON 파일 개수: {}", files.length);
        List<Environment> envs = jsonToAasxService.uploadJsonFiles(files);

        // 파싱된 환경(Environment) 개수가 업로드한 파일 개수와 다르면 오류
        if (envs.size() != files.length) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "일부 JSON 파일 파싱에 실패했습니다: 성공 " + envs.size() + " / 요청 " + files.length);
        }

        return ResponseEntity.ok(envs);
    }
}