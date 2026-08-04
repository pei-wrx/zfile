package com.pei.zfile.trash.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

@Data
public class TrashNodesRequest {
    @Min(1)
    private Integer page;

    @Min(1)
    @Max(100)
    private Integer size;
}
