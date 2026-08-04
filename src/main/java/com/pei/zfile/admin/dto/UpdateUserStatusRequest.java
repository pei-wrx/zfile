package com.pei.zfile.admin.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class UpdateUserStatusRequest {

    @NotNull(message = "用户状态不能为空")
    @Pattern(regexp = "ACTIVE|DISABLED", message = "用户状态只能是 ACTIVE 或 DISABLED")
    private String status;
}
