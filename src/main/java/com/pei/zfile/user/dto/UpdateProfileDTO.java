package com.pei.zfile.user.dto;

import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Email;
import lombok.Data;

@Data
public class UpdateProfileDTO {
    @Size(min = 3, max = 32, message = "用户名长度需在3-32位之间")
    private String username;

    @Email(message = "邮箱格式不正确")
    @Size(max = 128, message = "邮箱长度不能超过128位")
    private String email;
}