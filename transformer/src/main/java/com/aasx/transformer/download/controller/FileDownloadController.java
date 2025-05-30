package com.aasx.transformer.download.controller;

import com.aasx.transformer.download.service.FileDownloadService;
import com.aasx.transformer.upload.dto.FilesMeta;
import com.aasx.transformer.upload.service.FileUploadService;
import com.aasx.transformer.upload.service.JsonToAASXService;

import lombok.extern.slf4j.Slf4j;

import org.eclipse.digitaltwin.aas4j.v3.dataformat.aasx.AASXSerializer;
import org.eclipse.digitaltwin.aas4j.v3.dataformat.aasx.InMemoryFile;
import org.eclipse.digitaltwin.aas4j.v3.dataformat.xml.XmlSerializer;
import org.eclipse.digitaltwin.aas4j.v3.model.Environment;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/transformer")
public class FileDownloadController {

    @Value("${upload.temp-path}")
    private String tempPath;

    @Autowired
    private FileUploadService fileUploadService;

    @Autowired
    private JsonToAASXService jsonToAasxService;

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
    @GetMapping("/download/environment/{fileName:.+}")
    public ResponseEntity<Resource> downloadEnvironment(@PathVariable String fileName) {
        log.info("다운로드 요청된 파일 이름: {}", fileName);

        // computeSHA256HashesForInMemoryFiles() 로 해시 URL이 바인딩된 Environment map 을 구하고
        Map<String, Environment> updatedEnvironmentMap = fileUploadService.computeSHA256HashesForInMemoryFiles();

        // 해당 파일명이 없으면 404
        if (!updatedEnvironmentMap.containsKey(fileName)) {
            log.warn("요청된 파일 이름 '{}'에 해당하는 Environment 정보가 존재하지 않습니다.", fileName);
            return ResponseEntity.notFound().build();
        }

        // Environment 꺼내서 JSON Resource 로 직렬화
        Environment env = updatedEnvironmentMap.get(fileName);
        Resource jsonResource = fileDownloadService.downloadEnvironmentAsJson(env, fileName);

        String baseName = fileName.toLowerCase().endsWith(".aasx")
                ? fileName.substring(0, fileName.length() - 5)
                : fileName;
        String downloadName = baseName + ".json";

        // Content-Disposition 헤더 등 붙여서 ResponseEntity로 반환
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + downloadName + "\"")
                .contentType(MediaType.APPLICATION_JSON)
                .body(jsonResource);
    }

    /**
     * ✅ 첨부파일 다운로드
     *
     * @param hash 다운로드할 파일의 SHA-256 해시값 (저장된 파일명)
     * @return 파일을 포함한 ResponseEntity
     *
     *         브라우저에서 바로 파일을 열거나 저장
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

    // ======================= AASX Download =======================

    /**
     * ✅ URL 포함 AASX 패키지 다운로드 (tempPath에서 바로 읽음)
     */
    @GetMapping("/json/download/url/{fileName:.+}")
    public ResponseEntity<Resource> downloadWithUrlAasx(@PathVariable String fileName) throws IOException {
        log.info("URL AASX 다운로드 요청, fileName: {}", fileName);
        // fileName은 원래 JSON 파일명, ".json" 확장자 포함
        String base = fileName.toLowerCase().endsWith(".json")
                ? fileName.substring(0, fileName.length() - 5)
                : fileName;
        String aasxName = base + ".aasx";

        Path filePath = Path.of(tempPath, aasxName);
        if (!Files.exists(filePath)) {
            log.warn("Temp에 AASX 파일이 없습니다: {}", filePath);
            return ResponseEntity.notFound().build();
        }

        Resource resource = new FileSystemResource(filePath.toFile());
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + aasxName + "\"")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .contentLength(Files.size(filePath))
                .body(resource);
    }

    @GetMapping("/json/download/revert/{fileName:.+}")
    public ResponseEntity<Resource> downloadRevertedAasx(@PathVariable String fileName) throws IOException {
        // tempPath 에 저장된 `${baseName}.aasx` 파일을 읽어서 반환
        String base = fileName.toLowerCase().endsWith(".json")
                ? fileName.substring(0, fileName.length() - 5)
                : fileName;
        String aasxName = base + ".aasx";
        Path filePath = Path.of(tempPath, aasxName);
        if (!Files.exists(filePath)) {
            log.warn("Temp에 AASX 파일이 없습니다: {}", filePath);
            return ResponseEntity.notFound().build();
        }
        Resource resource = new FileSystemResource(filePath.toFile());
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + aasxName + "\"")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .contentLength(Files.size(filePath))
                .body(resource);
    }

}
