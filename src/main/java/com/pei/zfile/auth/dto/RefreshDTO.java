package com.pei.zfile.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class RefreshDTO {

    @NotBlank(message = "refreshToken 不能为空")
    @Size(min = 20, message = "refreshToken 格式不正确")
    private String refreshToken;
}
