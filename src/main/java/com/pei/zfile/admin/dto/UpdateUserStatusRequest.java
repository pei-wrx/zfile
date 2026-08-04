package com.pei.zfile.admin.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class UpdateUserStatusRequest {

    @NotNull(message = "用户状态不能为空")
    private String status;
}