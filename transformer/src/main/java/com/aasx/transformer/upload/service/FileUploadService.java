package com.aasx.transformer.upload.service;

import org.eclipse.digitaltwin.aas4j.v3.model.Environment;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.aasx.transformer.deserializer.AASXFileDeserializer;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

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

    private final List<String> uploadedFileNames = new CopyOnWriteArrayList<>();

    public List<Environment> uploadFiles(MultipartFile[] files) {
        List<Environment> results = new ArrayList<>();
        uploadedFileNames.clear(); // 기존 파일 목록 초기화

        for (MultipartFile file : files) {
            String fileName = file.getOriginalFilename();
            
            log.info("업로드된 파일 이름: {}", file.getOriginalFilename());
            log.info("업로드된 파일 크기: {}", file.getSize());

            try {
                String filePath = uploadPath + File.separator + file.getOriginalFilename();
                File destFile = new File(filePath);

                // upload 폴더가 없으면 생성
                if (uploadPath != null && !uploadPath.isEmpty()) {
                    File uploadDir = new File(uploadPath);
                    if (!uploadDir.exists()) {
                        uploadDir.mkdirs(); 
                        log.info("디렉토리 생성: {}", uploadPath);
                    } else {
                        log.info("디렉토리 존재");
                    }
                }
                
                // 파일을 실제로 디스크에 저장
                try (FileOutputStream fos = new FileOutputStream(destFile)) {
                    fos.write(file.getBytes());
                }

                // 파일이 디스크에 제대로 저장되었는지 확인
                log.info("파일이 저장된 경로: {}", destFile.getAbsolutePath());
                log.info("파일 저장 후 크기: {}", destFile.length());

                // AASX 파일을 처리하는 로직
                InputStream inputStream = file.getInputStream();

                // 파일 스트림 크기 디버깅
                // log.info("파일 스트림 크기: {}", inputStream.available());

                // AASX 파일 처리
                // JsonResults jsonResults = aasxFileDeserializer.deserializeAASXFile(inputStream);
                Environment environment = aasxFileDeserializer.deserializeAASXFile(inputStream);

                // 변환된 JSON 결과 확인
                /* if (jsonResults != null) {
                    results.add(jsonResults);
                } */

                if (environment != null) {
                    results.add(environment);
                }

                // 업로드된 파일 이름을 저장
                uploadedFileNames.add(fileName);

            } catch (Exception e) {
                log.error("파일 변환 중 오류 발생: {}", e.getMessage());
            }
        }
        return results;
    }

    public List<String> getUploadedFiles() {
        return new ArrayList<>(uploadedFileNames);
    }
}
