package com.aasx.transformer.download.controller;

import com.aasx.transformer.download.service.FileDownloadService;
import com.aasx.transformer.upload.dto.FilesMeta;
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
import java.util.List;
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
        log.info("다운로드 요청된 파일 이름: {}", fileName);
        Map<String, Environment> updatedEnvironmentMap = fileUploadService.computeSHA256HashesForInMemoryFiles();
        if (!updatedEnvironmentMap.containsKey(fileName)) {
            log.warn("요청된 파일 이름 '{}'에 해당하는 Environment 정보가 존재하지 않습니다.", fileName);
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
    @GetMapping("/download/{hashAndExt}")
    public ResponseEntity<Resource> downloadFile(@PathVariable("hashAndExt") String hashAndExt) {
        int dotIndex = hashAndExt.lastIndexOf(".");
        if (dotIndex < 0) {
            throw new RuntimeException("잘못된 파일명 형식입니다.");
        }
        String hash = hashAndExt.substring(0, dotIndex);
        // extension은 DB의 파일 메타에서 조회되므로 여기서는 hash만 사용하여 다운로드 처리
        Resource resource = fileDownloadService.downloadFileByHash(hash);
        String fileName = resource.getFilename();

        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileName + "\"");
        try {
            return ResponseEntity.ok()
                    .headers(headers)
                    .contentLength(resource.contentLength())
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .body(resource);
        } catch (IOException e) {
            throw new RuntimeException("파일 다운로드 처리 중 오류 발생", e);
        }
    }

    /**
     * 특정 패키지 파일에 속하는 첨부파일 메타 정보를 조회하는 엔드포인트  
     * URL 예시: /api/transformer/attachment/fileMetas/package/MyPackage.aasx  
     *  
     * 업로드 시 저장된 Environment 내에서 각 File 요소의 복합키 (aas id, submodel id, idShort)를 
     * 통해 DB에 등록된 파일 메타 정보를 조회합니다.
     */
    @GetMapping("/attachment/fileMetas/package/{packageFileName}")
    public ResponseEntity<List<FilesMeta>> listAttachmentFileMetasByPackageFile(
            @PathVariable("packageFileName") String packageFileName) {
        List<FilesMeta> metas = fileUploadService.getFileMetasByPackageFileName(packageFileName);
        return ResponseEntity.ok(metas);
    }
}
