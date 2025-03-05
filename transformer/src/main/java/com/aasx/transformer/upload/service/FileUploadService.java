package com.aasx.transformer.upload.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.aasx.transformer.deserializer.AASXFileDeserializer;

import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

@Slf4j
@Service
public class FileUploadService {

    @Autowired
    private AASXFileDeserializer aasxFileDeserializer;

    @Value("${upload.path}")
    private String uploadPath;

    // 업로드된 파일 이름을 저장할 리스트
    private final List<String> uploadedFileNames = new CopyOnWriteArrayList<>();

    public List<String> uploadFiles(MultipartFile[] files) {
        List<String> results = new ArrayList<>();

        for (MultipartFile file : files) {
            log.info("업로드된 파일 이름: {}", file.getOriginalFilename());
            log.info("업로드된 파일 크기: {}", file.getSize());

            try {
                String filePath = uploadPath + File.separator + file.getOriginalFilename();
                File destFile = new File(filePath);
                Files.createDirectories(Paths.get(uploadPath));

                // 파일을 실제로 디스크에 저장
                try (FileOutputStream fos = new FileOutputStream(destFile)) {
                    fos.write(file.getBytes());
                }

                // AASX 파일을 처리하는 로직
                InputStream inputStream = file.getInputStream();
                List<String> jsonResults = aasxFileDeserializer.deserializeAASXFile(inputStream);

                // 변환된 JSON 결과가 있으면 결과 리스트에 추가
                boolean isEmpty = jsonResults.isEmpty();
                boolean containsFailure = jsonResults.stream()
                        .anyMatch(result -> result.contains("실패"));

                if (isEmpty || containsFailure) {
                    log.error("AASX 파일 변환 실패: {}", file.getOriginalFilename());
                    results.add("변환 실패: " + file.getOriginalFilename());
                } else {
                    log.info("AASX 파일 변환 성공: {}", file.getOriginalFilename());
                    results.addAll(jsonResults);
                }

                // 업로드된 파일 이름을 저장
                uploadedFileNames.add(filePath);

            } catch (Exception e) {
                log.error("파일 변환 중 오류 발생: {}", e.getMessage());
                results.add("파일 변환 실패: " + file.getOriginalFilename());
            }
        }
        return results;
    }

    // 업로드된 파일 목록을 반환
    public List<String> getUploadedFiles() {
        return new ArrayList<>(uploadedFileNames);
    }
}
