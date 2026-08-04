package com.pei.zfile.file.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class BatchDeleteRequest {

    @NotEmpty
    @Size(min = 1, max = 100)
    private List<Long> nodeIds;
}