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
     * ✅ 특정 패키지 파일에 속하는 첨부파일 메타 정보를 조회하는 엔드포인트
     * 
     * 업로드 시 저장된 Environment 내에서 각 File 요소의 복합키 (aas id, submodel id, idShort)를
     * 통해 DB에 등록된 파일 메타 정보를 조회
     */
    @GetMapping("/package/{packageFileName:.+}")
    public ResponseEntity<List<FilesMeta>> listAttachmentFileMetasByPackageFile(
            @PathVariable("packageFileName") String packageFileName) {

        // 새 패키지 파일의 경우, 먼저 해시 계산 및 DB 업데이트(파일 메타 삽입)를 수행
        fileUploadService.computeSHA256HashesForInMemoryFiles();

        // 그 후, 업데이트된 파일 메타 정보를 조회
        List<FilesMeta> metas = fileDownloadService.getFileMetasByPackageFileName(packageFileName);
        return ResponseEntity.ok(metas);
    }

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
     * 
     * @param hash 다운로드할 파일의 SHA-256 해시값 (저장된 파일명)
     * @return 파일을 포함한 ResponseEntity
     */
    @GetMapping("/download/{hashAndExt}")
    public ResponseEntity<Resource> downloadFile(@PathVariable("hashAndExt") String hashAndExt) {
        // 전달받은 파일명 문자열에서 점(.)을 기준으로 해시를 추출한 후, DB에 저장된 파일 메타를 사용하여 물리 파일 Resource를 가져옴
        // 예를 들어, 파일명이 "19b5caffb6a8a22427e5a947868d7910.png"이면,
        // dotIndex : "19b5caffb6a8a22427e5a947868d7910"를 해시로 사용
        int dotIndex = hashAndExt.lastIndexOf(".");
        if (dotIndex < 0) {
            throw new RuntimeException("잘못된 파일명 형식입니다.");
        }
        String hash = hashAndExt.substring(0, dotIndex);
        // 확장자 정보는 DB에 저장된 파일 메타를 통해 확인
        // 따라서 hash만 사용하여 다운로드 처리
        // 클라이언트에서 호출할 때는, meta.hash와 meta.extension이 별도로 저장되어 있어 "hash + extension" 형태의
        // 문자열
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
     * ✅ 첨부파일 메타 삭제
     * 
     * @param compositeKey "aasId::submodelId::idShort" 형태의 복합키
     */
    @DeleteMapping("/delete/file")
    public ResponseEntity<Void> deleteAttachmentMeta(@RequestParam("compositeKey") String compositeKey) {
        log.info("삭제할 compositeKey = {}", compositeKey);
        fileUploadService.deleteFileMeta(compositeKey);
        return ResponseEntity.noContent().build();
    }

}
