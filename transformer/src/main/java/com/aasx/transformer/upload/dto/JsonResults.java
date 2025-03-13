package com.aasx.transformer.upload.dto;

import lombok.Data;

@Data
public class JsonResults {
    private Object jsonResult;

    // Object를 받는 생성자 추가
    public JsonResults(Object jsonResult) {
        this.jsonResult = jsonResult;
    }
}