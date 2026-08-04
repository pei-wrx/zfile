package com.pei.zfile.admin.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UserPageRequest {

    @Size(max = 100)
    private String keyword;

    private String status;

    @Min(1)
    private Integer page;

    @Min(1)
    @Max(100)
    private Integer size;
}