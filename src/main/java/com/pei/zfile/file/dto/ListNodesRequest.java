package com.pei.zfile.file.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ListNodesRequest {

    private Long parentId;

    @Size(max = 100)
    private String keyword;

    private String type;

    private String sort;

    private String direction;

    @Min(1)
    private Integer page;

    @Min(1)
    @Max(100)
    private Integer size;
}