package com.pei.zfile.common.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PageResult<T> {

    private List<T> items;
    private int page;
    private int size;
    private long total;
    private int pages;

    public static <T> PageResult<T> of(List<T> items, int page, int size, long total) {
        int pages = size > 0 ? (int) Math.ceil((double) total / size) : 0;
        return new PageResult<>(items, page, size, total, pages);
    }
}