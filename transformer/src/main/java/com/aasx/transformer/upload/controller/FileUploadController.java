package com.aasx.transformer.upload.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;


import com.aasx.transformer.deserializer.AASXFileDeserializer;
import com.aasx.transformer.upload.service.FileUploadService;
import com.aasx.transformer.upload.service.JsonToAASXService;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;


import java.util.List;
import java.util.stream.Collectors;


import org.eclipse.digitaltwin.aas4j.v3.model.Environment;

@Slf4j
@RestController
@RequestMapping("/api/transformer")
public class FileUploadController {

    @Autowired
    private FileUploadService fileUploadService;

    @Autowired
    private JsonToAASXService jsonToAasxService;

    @Autowired
    private AASXFileDeserializer aasxFileDeserializer;

    // ✅ 여러 개의 파일 업로드
    @PostMapping("/aasx")
    public ResponseEntity<List<String>> uploadAasxAndGetJson(
            @RequestParam("files") MultipartFile[] files) {

        // 1) 저장 & Environment 객체 생성/등록
        List<Environment> envs = fileUploadService.uploadFiles(files);

        // 2) JSON 문자열로 직렬화
        List<String> jsonList = envs.stream()
            .map(aasxFileDeserializer::serializeEnvironmentToJson)
            .collect(Collectors.toList());

        return ResponseEntity.ok(jsonList);
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
            @RequestParam("files") MultipartFile[] jsonFiles) {
        List<Environment> envs = jsonToAasxService.uploadJsonFiles(jsonFiles);
        return ResponseEntity.ok(envs);
    }
}