package com.pei.zfile.file.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;
import java.util.List;

@Data
public class CopyNodesRequest {

    @NotEmpty
    @Size(min = 1, max = 100)
    private List<@NotNull Long> nodeIds;

    private Long targetParentId;

    @NotNull
    @Pattern(regexp = "REJECT|RENAME", message = "冲突策略只能是 REJECT 或 RENAME")
    private String conflictPolicy;
}
