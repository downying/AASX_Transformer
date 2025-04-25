package com.aasx.transformer.admin.controller;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.aasx.transformer.admin.service.AdminService;
import com.aasx.transformer.upload.dto.Files;
import com.aasx.transformer.upload.dto.FilesMeta;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequestMapping("/api/transformer")
public class AdminController {

    @Autowired
    private AdminService admindService;

    // ✅ 모든 파일 해시(ref_count, size 포함) 반환
    @GetMapping("/files")
    public ResponseEntity<List<Files>> listAllFileHash() {
        List<Files> allFiles = admindService.getAllFileHash();
        log.info("전체 파일 해시 조회: {}건", allFiles.size());
        return ResponseEntity.ok(allFiles);
    }

    // ✅ DB에 저장된 모든 파일 메타 정보를 반환
    @GetMapping("/file-metas")
    public ResponseEntity<List<FilesMeta>> listAllFileMetas() {
        List<FilesMeta> allMetas = admindService.getAllFileMetas();
        log.info("전체 파일 메타 조회: {}건", allMetas.size());
        return ResponseEntity.ok(allMetas);
    }

}
