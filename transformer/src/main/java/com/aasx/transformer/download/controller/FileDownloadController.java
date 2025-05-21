package com.aasx.transformer.download.controller;

import com.aasx.transformer.download.service.FileDownloadService;
import com.aasx.transformer.upload.dto.FilesMeta;
import com.aasx.transformer.upload.service.FileUploadService;

import lombok.extern.slf4j.Slf4j;

import org.eclipse.digitaltwin.aas4j.v3.model.Environment;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

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
            @PathVariable String packageFileName) {

        List<FilesMeta> metas = fileDownloadService.getFileMetasByPackageFileName(packageFileName);
        return ResponseEntity.ok(metas);
    }

    /**
     * ✅ 특정 파일의 업데이트된 Environment JSON 다운로드
     * 예시 URL: /api/transformer/download/environment/{fileName}
     */
    @GetMapping("/download/environment/{fileName}")
    public ResponseEntity<Resource> downloadEnvironment(@PathVariable String fileName) {
        log.info("다운로드 요청된 파일 이름: {}", fileName);
        Map<String, Environment> updatedEnvironmentMap = fileUploadService.computeSHA256HashesForInMemoryFiles();
        if (!updatedEnvironmentMap.containsKey(fileName)) {
            log.warn("요청된 파일 이름 '{}'에 해당하는 Environment 정보가 존재하지 않습니다.", fileName);
            return ResponseEntity.notFound().build();
        }
        Environment env = updatedEnvironmentMap.get(fileName);
        return fileDownloadService.downloadEnvironmentAsJson(env, fileName);
    }

    /**
     * ✅ 첨부파일 다운로드
     * 
     * @param hash 다운로드할 파일의 SHA-256 해시값 (저장된 파일명)
     * @return 파일을 포함한 ResponseEntity
     * 
     * 브라우저에서 바로 파일을 열거나 저장
     */
    @GetMapping("/download/{hashAndExt:.+}")
    public ResponseEntity<Resource> downloadFile(@PathVariable String hashAndExt) {
        // 1) Hash와 Extension 분리 시도
        int dot = hashAndExt.lastIndexOf('.');
        String hash;
        String ext;
        if (dot > 0) {
            hash = hashAndExt.substring(0, dot);
            ext = hashAndExt.substring(dot); // ".png" 등
        } else {
            // 확장자가 없이 호출된 경우
            hash = hashAndExt;
            // DB에서 메타를 먼저 조회하여 확장자를 꺼냄
            FilesMeta meta0 = fileDownloadService.getMetaByHash(hash);
            if (meta0 == null) {
                return ResponseEntity.notFound().build();
            }
            ext = meta0.getExtension(); // ".jpg", ".pdf" 등
        }

        // 2) DB에서 메타 조회
        FilesMeta meta = fileDownloadService.getMetaByHash(hash);
        if (meta == null) {
            return ResponseEntity.notFound().build();
        }

        // 3) 물리 파일 로드
        Resource resource = fileDownloadService.downloadFileByHash(hash);

        // 4) inline 미리보기 헤더 설정
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.CONTENT_DISPOSITION,
                "inline; filename=\"" + hash + ext + "\"");

        MediaType mediaType = MediaType.parseMediaType(meta.getContentType());
        try {
            return ResponseEntity.ok()
                    .headers(headers)
                    .contentLength(resource.contentLength())
                    .contentType(mediaType)
                    .body(resource);
        } catch (IOException e) {
            throw new RuntimeException("파일 응답 처리 중 오류", e);
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
