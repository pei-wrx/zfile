package com.pei.zfile.file.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class MoveNodeRequest {

    private Long targetParentId;

    @NotNull
    @Pattern(regexp = "REJECT|RENAME", message = "冲突策略只能是 REJECT 或 RENAME")
    private String conflictPolicy;
}
