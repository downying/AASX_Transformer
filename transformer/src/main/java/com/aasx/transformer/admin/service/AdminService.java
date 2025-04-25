package com.aasx.transformer.admin.service;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.aasx.transformer.upload.dto.Files;
import com.aasx.transformer.upload.dto.FilesMeta;
import com.aasx.transformer.upload.mapper.UploadMapper;

import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class AdminService {
    
    @Autowired
    private UploadMapper uploadMapper;

    // ✅ DB에 저장된 모든 파일 해시와 ref_count, size를 조회
    public List<Files> getAllFileHash() {
        return uploadMapper.selectAllFileHash();
    }

    // ✅ DB에 저장된 모든 파일 메타 정보를 조회
    public List<FilesMeta> getAllFileMetas() {
        return uploadMapper.selectAllFileMetas();
    }

}
