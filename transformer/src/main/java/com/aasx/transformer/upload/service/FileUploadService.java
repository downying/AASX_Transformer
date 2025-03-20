package com.aasx.transformer.upload.service;

import org.apache.poi.openxml4j.exceptions.InvalidFormatException;
import org.apache.poi.openxml4j.opc.OPCPackage;
import org.eclipse.digitaltwin.aas4j.v3.dataformat.aasx.InMemoryFile;
import org.eclipse.digitaltwin.aas4j.v3.model.Environment;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.aasx.transformer.deserializer.AASXFileDeserializer;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

@Service
@Slf4j
public class FileUploadService {

    @Autowired
    private AASXFileDeserializer aasxFileDeserializer;

    @Value("${upload.path}")
    private String uploadPath;

    private final List<String> uploadedFileNames = new CopyOnWriteArrayList<>();
    // AASX 파일에서 읽은 환경을 저장
    private final List<Environment> uploadedEnvironments = new CopyOnWriteArrayList<>(); 

    // 기존의 파일 업로드 메소드
    public List<Environment> uploadFiles(MultipartFile[] files) {
        List<Environment> results = new ArrayList<>();
        uploadedFileNames.clear(); // 기존 파일 목록 초기화
        uploadedEnvironments.clear(); // 기존 환경 초기화

        for (MultipartFile file : files) {
            String fileName = file.getOriginalFilename();
            log.info("업로드된 파일 이름: {}", file.getOriginalFilename());
            log.info("업로드된 파일 크기: {}", file.getSize());

            try {
                String filePath = uploadPath + File.separator + file.getOriginalFilename();
                File destFile = new File(filePath);

                // 업로드 디렉토리 생성
                if (uploadPath != null && !uploadPath.isEmpty()) {
                    File uploadDir = new File(uploadPath);
                    if (!uploadDir.exists()) {
                        uploadDir.mkdirs();
                        log.info("디렉토리 생성: {}", uploadPath);
                    } else {
                        log.info("디렉토리 존재");
                    }
                }

                // 파일 디스크에 저장
                try (FileOutputStream fos = new FileOutputStream(destFile)) {
                    fos.write(file.getBytes());
                }

                log.info("파일 저장 후 경로: {}", destFile.getAbsolutePath());

                // AASX 파일 처리
                InputStream inputStream = file.getInputStream();
                Environment environment = aasxFileDeserializer.deserializeAASXFile(inputStream);

                if (environment != null) {
                    results.add(environment);
                    uploadedEnvironments.add(environment); // 환경 정보 저장
                }

                uploadedFileNames.add(fileName); // 업로드된 파일 이름 저장

            } catch (Exception e) {
                log.error("파일 변환 중 오류 발생: {}", e.getMessage());
            }
        }

        return results;
    }

    // 업로드된 파일 이름 조회
    public List<String> getUploadedFiles() {
        return new ArrayList<>(uploadedFileNames);
    }

    // 업로드된 AASX 파일에서 참조된 파일 경로 조회
   /*  public Map<String, List<String>> getReferencedFilePaths() {
        Map<String, List<String>> filePathsMap = new HashMap<>();
        for (int i = 0; i < uploadedFileNames.size(); i++) {
            String fileName = uploadedFileNames.get(i);
            Environment environment = uploadedEnvironments.get(i);

            // Environment에서 참조된 파일 경로들 추출
            List<String> paths = aasxFileDeserializer.parseReferencedFilePathsFromAASX(environment);
            filePathsMap.put(fileName, paths);
        }
        return filePathsMap;
    } */

    // InMemoryFile로 변환
    public Map<String, List<InMemoryFile>> getInMemoryFilesFromReferencedPaths() {
        Map<String, List<InMemoryFile>> inMemoryFilesMap = new HashMap<>();

        // 업로드된 파일 목록과 environment 목록은 같은 인덱스를 가짐
        for (int i = 0; i < uploadedFileNames.size(); i++) {
            String fileName = uploadedFileNames.get(i);
            Environment environment = uploadedEnvironments.get(i);

            // Environment에서 참조된 파일 경로들 추출
            List<String> paths = aasxFileDeserializer.parseReferencedFilePathsFromAASX(environment);

            if (paths.isEmpty()) {
                // 참조 경로가 없다면 비어있는 리스트 매핑
                inMemoryFilesMap.put(fileName, Collections.emptyList());
                continue;
            }

            // 실제 디스크에 저장된 AASX 파일 열기
            String aasxFilePath = uploadPath + File.separator + fileName;
            File aasxFile = new File(aasxFilePath);

            // OPCPackage로 AASX 내부 리소스를 읽어옴
            try (OPCPackage aasxRoot = OPCPackage.open(aasxFile)) {
                List<InMemoryFile> inMemoryFiles = aasxFileDeserializer.readFiles(aasxRoot, paths);
                inMemoryFilesMap.put(fileName, inMemoryFiles);
            } catch (InvalidFormatException | IOException e) {
                log.error("AASX 내부 파일 읽기 오류 ({}): {}", fileName, e.getMessage(), e);
                inMemoryFilesMap.put(fileName, Collections.emptyList());
            }
            
        }

        return inMemoryFilesMap;
    }
}