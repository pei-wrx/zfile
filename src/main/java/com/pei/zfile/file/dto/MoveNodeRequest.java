package com.pei.zfile.file.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class MoveNodeRequest {

    private Long targetParentId;

    @NotNull
    private String conflictPolicy;
}