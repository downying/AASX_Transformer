package com.aasx.transformer.upload.controller;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.core.io.Resource;

import com.aasx.transformer.deserializer.AASXFileDeserializer;
import com.aasx.transformer.upload.service.FileUploadService;
import com.aasx.transformer.upload.service.JsonToAASXService;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.InputStreamResource;

import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.eclipse.digitaltwin.aas4j.v3.dataformat.aasx.AASXDeserializer;
import org.eclipse.digitaltwin.aas4j.v3.dataformat.aasx.AASXSerializer;
import org.eclipse.digitaltwin.aas4j.v3.dataformat.aasx.InMemoryFile;
import org.eclipse.digitaltwin.aas4j.v3.dataformat.core.DeserializationException;
import org.eclipse.digitaltwin.aas4j.v3.dataformat.core.SerializationException;
import org.eclipse.digitaltwin.aas4j.v3.dataformat.json.JsonSerializer;
import org.eclipse.digitaltwin.aas4j.v3.dataformat.xml.XmlSerializer;
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
    public ResponseEntity<List<String>> uploadFile(
            @RequestParam("files") MultipartFile[] files) {

        // 1) .aasx 파일 저장 & Environment 리스트 생성
        List<Environment> environments = fileUploadService.uploadFiles(files);

        // 2) InMemoryFile 해시 계산 → DB에 FilesMeta 등록
        fileUploadService.computeSHA256HashesForInMemoryFiles();

        // 3) JSON 문자열로 직렬화해서 반환
        List<String> jsonList = environments.stream()
                .map(env -> aasxFileDeserializer.serializeEnvironmentToJson(env))
                .collect(Collectors.toList());

        log.info("uploadFile → 변환된 JSON 목록: {}", jsonList);
        return ResponseEntity.ok(jsonList);
    }

    // ✅ 업로드된 파일 이름 조회
    @GetMapping("/uploadedFileNames")
    public ResponseEntity<List<String>> listUploadedFiles() {
        List<String> uploadedFileNames = fileUploadService.getUploadedFiles();
        log.info("컨트롤러 - 업로드된 파일 이름 조회: {}", uploadedFileNames);
        return ResponseEntity.ok(uploadedFileNames);
    }

    // ======================= AASX Download =======================

    // ✅ 업로드된 JSON 파일 이름 조회
    @GetMapping("/uploadedJsonFileNames")
    public ResponseEntity<List<String>> listUploadedJsonFiles() {
        List<String> jsonFileNames = jsonToAasxService.getUploadedJsonFileNames();
        log.info("컨트롤러 - 업로드된 JSON 파일 이름 조회: {}", jsonFileNames);
        return ResponseEntity.ok(jsonFileNames);
    }

    /**
     * ✅ JSON → AASX 패키지 생성 (URL-only, revert-files 두 가지 variant)
     *    업로드된 JSON을 바탕으로 두 가지 AASX를 모두 생성하고
     *    생성된 파일명 리스트를 그대로 반환합니다.
     */
    @PostMapping("/json")
    public ResponseEntity<List<String>> uploadJson(@RequestPart("files") MultipartFile[] files) {
        log.info("JSON → AASX 패키지 생성 요청, 파일 수: {}", files.length);
        List<String> names = jsonToAasxService.generateAasxVariants(files);
        log.info("uploadJson → 생성된 AASX 파일: {}", names);
        return ResponseEntity.ok(names);
    }
}