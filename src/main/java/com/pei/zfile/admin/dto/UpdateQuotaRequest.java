package com.pei.zfile.admin.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class UpdateQuotaRequest {

    @NotNull(message = "配额不能为空")
    @Min(value = 0, message = "配额不能为负数")
    private Long quotaBytes;
}