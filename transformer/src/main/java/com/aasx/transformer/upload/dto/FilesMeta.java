package com.aasx.transformer.upload.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class FilesMeta {
    private String AasId;
    private String SubmodelId;
    private String IdShort;
    private String name;
    private String extension;
    private String contentType;
    private String hash;
}
