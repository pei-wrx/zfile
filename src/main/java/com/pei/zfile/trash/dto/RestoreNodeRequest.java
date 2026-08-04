package com.pei.zfile.trash.dto;

import lombok.Data;
import jakarta.validation.constraints.Pattern;

@Data
public class RestoreNodeRequest {

    private Long targetParentId;

    @Pattern(regexp = "REJECT|RENAME", message = "冲突策略只能是 REJECT 或 RENAME")
    private String conflictPolicy;
}
