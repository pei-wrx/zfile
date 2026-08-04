package com.pei.zfile.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UpdatePwdDTO {

    @NotBlank(message = "原密码不能为空")
    private String currentPassword;

    @NotBlank(message = "新密码不能为空")
    @Size(min = 6, max = 128, message = "密码长度需在6-128位之间")
    private String newPassword;
}
