package com.aasx.transformer.admin.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.aasx.transformer.admin.dto.PageResponse;
import com.aasx.transformer.admin.service.AdminService;
import com.aasx.transformer.upload.dto.Files;
import com.aasx.transformer.upload.dto.FilesMeta;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequestMapping("/api/transformer")
public class AdminController {

    @Autowired
    private AdminService adminService;

    // ✅ 모든 파일 해시(ref_count, size 포함) 반환
    @GetMapping("/files")
    public ResponseEntity<PageResponse<Files>> listAllFileHash(
            @RequestParam(defaultValue = "0") int offset,
            @RequestParam(defaultValue = "20") int limit) {
        PageResponse<Files> result = adminService.getPagedFileHashes(offset, limit);
        log.info("파일 해시 조회 - offset: {}, limit: {}, 총: {}", offset, limit, result.getTotalCount());
        return ResponseEntity.ok(result);
    }

    // ✅ DB에 저장된 모든 파일 메타 정보를 반환
    @GetMapping("/file-metas")
    public ResponseEntity<PageResponse<FilesMeta>> listAllFileMetas(
            @RequestParam(defaultValue = "0") int offset,
            @RequestParam(defaultValue = "20") int limit) {
        PageResponse<FilesMeta> result = adminService.getPagedFileMetas(offset, limit);
        log.info("파일 메타 조회 - offset: {}, limit: {}, 총: {}", offset, limit, result.getTotalCount());
        return ResponseEntity.ok(result);
    }

}
