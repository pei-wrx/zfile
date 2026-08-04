package com.pei.zfile.file.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import java.util.List;

@Data
public class CopyNodesRequest {

    @NotEmpty
    @Size(min = 1, max = 100)
    private List<Long> nodeIds;

    private Long targetParentId;

    @NotNull
    private String conflictPolicy;
}
