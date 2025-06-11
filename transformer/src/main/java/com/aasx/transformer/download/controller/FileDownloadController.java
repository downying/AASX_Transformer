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
import java.util.Collections;
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

        // 1) AASX 업로드된 이름이면 기존 로직 실행
        if (fileUploadService.getUploadedFileNames().contains(packageFileName)) {
            List<FilesMeta> metas = fileDownloadService.getFileMetasByPackageFileName(packageFileName);
            return ResponseEntity.ok(metas);
        }

        // 2) JSON→AASX 변환된 이름이면 JsonToAASXService 로직 실행
        if (jsonToAasxService.getUploadedJsonFileNames().contains(packageFileName)) {
            List<FilesMeta> metas = fileDownloadService.getJsonConvertedFileMetas(packageFileName);
            return ResponseEntity.ok(metas);
        }

        // 3) 그 외: 빈 리스트 응답 (경고 로그 없이)
        return ResponseEntity.ok(Collections.emptyList());
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
        // 1) 해시/확장자 분리
        int dot = hashAndExt.lastIndexOf('.');
        String hash, ext;
        if (dot > 0) {
            hash = hashAndExt.substring(0, dot);
            ext = hashAndExt.substring(dot); // ".png" 등
        } else {
            hash = hashAndExt;
            FilesMeta meta0 = fileDownloadService.getMetaByHash(hash);
            if (meta0 == null) {
                return ResponseEntity.notFound().build();
            }
            ext = meta0.getExtension(); // ".jpg" 등
        }

        // 2) DB에서 메타 조회
        FilesMeta meta = fileDownloadService.getMetaByHash(hash);
        if (meta == null) {
            return ResponseEntity.notFound().build();
        }

        // contentType을 확인
        String contentType = meta.getContentType();
        log.info("==> FilesMeta.getContentType() = [{}]", contentType);

        // 3) 물리 파일 로드
        Resource resource = fileDownloadService.downloadFileByHash(hash);
        if (!resource.exists()) {
            return ResponseEntity.notFound().build();
        }

        // 4) 올바른 MIME 타입
        MediaType mediaType;
        try {
            mediaType = MediaType.parseMediaType(meta.getContentType());
        } catch (Exception e) {
            // 만약 meta.getContentType()이 잘못된 값이라면, 안전하게 이미지/바이너리로 처리
            mediaType = MediaType.APPLICATION_OCTET_STREAM;
        }

        // 5) inline 으로 보내되, Accept-Ranges를 함께 달아서 브라우저에 “바이너리 원격 다운로드” 기능을 알려준다
        HttpHeaders headers = new HttpHeaders();
        // inline → 브라우저 뷰어(이미지/PDF 뷰어 등)를 사용하도록 요청
        headers.add(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + hash + ext + "\"");
        // 바이트 단위 범위 요청을 허용하도록
        headers.add("Accept-Ranges", "bytes");

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
     * ✅ URL-only AASX 패키지 다운로드
     * 프론트엔드에서는 이미 “{base}-url.aasx” 형태로 호출함.
     */
    @GetMapping("/json/download/url/{aasxFileName:.+}")
    public ResponseEntity<Resource> downloadWithUrlAasx(@PathVariable String aasxFileName) throws IOException {
        log.info("URL AASX 다운로드 요청, aasxFileName: {}", aasxFileName);
        // note: aasxFileName 예시 → "BALL_END_BOSS_ONE_DPP_demo_edited_v3-url.aasx"
        Path filePath = Path.of(tempPath, aasxFileName);
        if (!Files.exists(filePath)) {
            log.warn("Temp에 AASX 파일이 없습니다: {}", filePath);
            return ResponseEntity.notFound().build();
        }

        Resource resource = new FileSystemResource(filePath.toFile());
        return ResponseEntity.ok()
                // attachment 헤더: 브라우저가 “파일 저장” 대화상자를 띄우도록 함
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + aasxFileName + "\"")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .contentLength(Files.size(filePath))
                .body(resource);
    }

    /**
     * ✅ Revert/embed AASX 패키지 다운로드
     * 프론트엔드에서는 이미 “{base}-revert.aasx” 형태로 호출함.
     */
    @GetMapping("/json/download/revert/{aasxFileName:.+}")
    public ResponseEntity<Resource> downloadRevertedAasx(@PathVariable String aasxFileName) throws IOException {
        log.info("Revert AASX 다운로드 요청, aasxFileName: {}", aasxFileName);
        // aasxFileName 예시 → "BALL_END_BOSS_ONE_DPP_demo_edited_v3-revert.aasx"
        Path filePath = Path.of(tempPath, aasxFileName);
        if (!Files.exists(filePath)) {
            log.warn("Temp에 AASX 파일이 없습니다: {}", filePath);
            return ResponseEntity.notFound().build();
        }

        Resource resource = new FileSystemResource(filePath.toFile());
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + aasxFileName + "\"")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .contentLength(Files.size(filePath))
                .body(resource);
    }

}
