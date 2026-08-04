package com.pei.zfile.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class RegisterDTO {

    @NotBlank(message = "用户名不能为空")
    @Size(min = 4, max = 32, message = "用户名长度需在4-32位之间")
    @Pattern(regexp = "^[A-Za-z0-9_]+$", message = "用户名只能包含字母、数字和下划线")
    private String username;

    @NotBlank(message = "邮箱不能为空")
    @Email(message = "邮箱格式不正确")
    @Size(max = 128, message = "邮箱长度不能超过128位")
    private String email;

    @NotBlank(message = "密码不能为空")
    @Size(min = 8, max = 72, message = "密码长度需在8-72位之间")
    private String password;
}
