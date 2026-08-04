package com.pei.zfile.file.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class ListNodesRequest {

    private Long parentId;

    @Size(max = 100)
    private String keyword;

    @Pattern(regexp = "FILE|FOLDER", message = "节点类型只能是 FILE 或 FOLDER")
    private String type;

    @Pattern(regexp = "name|size|createdAt|updatedAt", message = "排序字段不正确")
    private String sort;

    @Pattern(regexp = "asc|desc", flags = Pattern.Flag.CASE_INSENSITIVE, message = "排序方向只能是 asc 或 desc")
    private String direction;

    @Min(1)
    private Integer page;

    @Min(1)
    @Max(100)
    private Integer size;
}
