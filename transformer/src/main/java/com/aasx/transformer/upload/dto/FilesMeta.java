package com.aasx.transformer.upload.dto;

import lombok.Data;

@Data
public class FilesMeta {
    private String path;
    private String name;
    private String extension;
    private String contentType;
    private int size;
    private String hash;
}
