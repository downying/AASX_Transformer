package com.aasx.transformer.upload.service;

import org.apache.poi.openxml4j.exceptions.InvalidFormatException;
import org.apache.poi.openxml4j.opc.OPCPackage;
import org.eclipse.digitaltwin.aas4j.v3.dataformat.aasx.InMemoryFile;
import org.eclipse.digitaltwin.aas4j.v3.model.Environment;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.aasx.transformer.deserializer.AASXFileDeserializer;
import com.aasx.transformer.deserializer.SHA256Hash;
import com.aasx.transformer.deserializer.SHA256HashApache;

import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

@Service
@Slf4j
public class FileUploadService {

    @Autowired
    private AASXFileDeserializer aasxFileDeserializer;

    @Value("${upload.path}")
    private String uploadPath;

    // AASX 파일 이름
    private final List<String> uploadedFileNames = new CopyOnWriteArrayList<>();
    // AASX 파일에서 읽은 환경을 저장
    private final List<Environment> uploadedEnvironments = new CopyOnWriteArrayList<>();

    // ✅ 기존의 파일 업로드 메소드
    public List<Environment> uploadFiles(MultipartFile[] files) {
        List<Environment> results = new ArrayList<>();
        uploadedFileNames.clear(); // 기존 파일 목록 초기화
        uploadedEnvironments.clear(); // 기존 환경 초기화

        for (MultipartFile file : files) {
            String fileName = file.getOriginalFilename();
            log.info("업로드된 파일 이름: {}", file.getOriginalFilename());
            log.info("업로드된 파일 크기: {}", file.getSize());

            try {

                // 확장자 검사: 파일명이 .aasx로 끝나지 않으면 스킵
                if (fileName == null || !fileName.toLowerCase().endsWith(".aasx")) {
                    log.warn("파일이 AASX 형식이 아니므로 업로드 처리에서 제외합니다: {}", fileName);
                    continue; // 다음 파일로 넘어감
                }
                
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

    /*
     * ✅ 업로드된 AASX 파일에서 참조된 파일 경로 조회
     * public Map<String, List<String>> getReferencedFilePaths() {
     * Map<String, List<String>> filePathsMap = new HashMap<>();
     * for (int i = 0; i < uploadedFileNames.size(); i++) {
     * String fileName = uploadedFileNames.get(i);
     * Environment environment = uploadedEnvironments.get(i);
     * 
     * // Environment에서 참조된 파일 경로들 추출
     * List<String> paths =
     * aasxFileDeserializer.parseReferencedFilePathsFromAASX(environment);
     * filePathsMap.put(fileName, paths);
     * }
     * return filePathsMap;
     * }
     */

    // ✅ InMemoryFile로 변환
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

    /*
     * ✅ SHA-256 해시값 계산
     * public Map<String, List<String>> computeSHA256HashesForInMemoryFiles() {
     * Map<String, List<InMemoryFile>> inMemoryFilesMap =
     * getInMemoryFilesFromReferencedPaths();
     * Map<String, List<String>> sha256HashesMap = new HashMap<>();
     * 
     * // entrySet(): 맵에 저장된 모든 키-값 쌍(각각 Map.Entry<String, List<InMemoryFile>> 타입)을
     * 포함하는 Set을 반환
     * // Map.Entry<String, List<InMemoryFile>>: 타입
     * // 반복문으로 순회하면 각 항목(각 Entry)을 하나씩 가져옴
     * for (Map.Entry<String, List<InMemoryFile>> entry :
     * inMemoryFilesMap.entrySet()) {
     * String fileName = entry.getKey();
     * List<InMemoryFile> inMemoryFiles = entry.getValue();
     * 
     * // InMemoryFile 객체의 SHA-256 해시값을 저장할 리스트를 초기화
     * List<String> fileHashes = new ArrayList<>();
     * for (InMemoryFile inMemoryFile : inMemoryFiles) {
     * // SHA256Hash 클래스의 메소드를 사용하여 해시값 계산
     * // String hash = SHA256Hash.computeSHA256Hash(inMemoryFile);
     * String hash = SHA256HashApache.computeSHA256Hash(inMemoryFile);
     * fileHashes.add(hash);
     * }
     * 
     * sha256HashesMap.put(fileName, fileHashes);
     * }
     * 
     * return sha256HashesMap;
     * }
     */

    // ✅ 동일 파일 검색 및 해시값 기반 파일 저장
    public Map<String, List<String>> computeSHA256HashesForInMemoryFiles() {
        // AASX 파일에서 추출된 첨부파일들을 파일명별로 매핑한 Map
        Map<String, List<InMemoryFile>> inMemoryFilesMap = getInMemoryFilesFromReferencedPaths();

        // key가 해시값(최종 파일명), value는 해당 해시값을 담은 리스트
        Map<String, List<String>> sha256HashesMap = new HashMap<>();

        // 모든 InMemoryFile에 대해 반복 (원본 파일명은 사용하지 않음)
        for (List<InMemoryFile> inMemoryFiles : inMemoryFilesMap.values()) {
            for (InMemoryFile inMemoryFile : inMemoryFiles) {
                try {
                    String hash = SHA256HashApache.computeSHA256Hash(inMemoryFile);

                    // 확장자 추출 (원본 파일 경로에서 추출)
                    String extension = "";
                    String originalPath = inMemoryFile.getPath();
                    if (originalPath != null && originalPath.contains(".")) {
                        extension = originalPath.substring(originalPath.lastIndexOf("."));
                    }
                    // 해시값 기반 파일명 생성 (확장자 포함)
                    String newFileName = hash + extension;
                    String newFilePath = uploadPath + File.separator + newFileName;
                    File newFile = new File(newFilePath);

                    // 파일이 존재하지 않으면 저장
                    if (!newFile.exists()) {
                        try (FileOutputStream fos = new FileOutputStream(newFile)) {
                            fos.write(inMemoryFile.getFileContent());
                        }
                        log.info("첨부파일 저장: 해시값 {} 기반 파일명 {} 으로 저장됨", hash, newFileName);
                    } else {
                        log.info("중복 파일 발견: {} (이미 저장됨)", newFileName);
                    }

                    // 해시맵에 등록 (파일명 정보 포함)
                    if (!sha256HashesMap.containsKey(hash)) {
                        sha256HashesMap.put(hash, Collections.singletonList(newFileName));
                    } else {
                        log.info("이미 등록된 해시값: {}", hash);
                    }
                } catch (Exception e) {
                    log.error("해시 계산 중 오류 발생: {}", e.getMessage(), e);
                }
            }
        }
        log.info("sha256HashesMap: {}", sha256HashesMap);
        return sha256HashesMap;
    }

}