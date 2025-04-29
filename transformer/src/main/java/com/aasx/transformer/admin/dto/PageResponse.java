package com.aasx.transformer.admin.dto;

import java.util.List;

public class PageResponse<T> {
    private List<T> items;
    private int totalCount;

    public PageResponse(List<T> items, int totalCount) {
        this.items = items;
        this.totalCount = totalCount;
    }

    public List<T> getItems() {
        return items;
    }

    public int getTotalCount() {
        return totalCount;
    }
}
