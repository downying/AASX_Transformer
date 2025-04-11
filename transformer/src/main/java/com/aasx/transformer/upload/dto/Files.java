package com.aasx.transformer.upload.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class Files {
    private String hash;
    private Long size;
    private Integer refCount;
}
