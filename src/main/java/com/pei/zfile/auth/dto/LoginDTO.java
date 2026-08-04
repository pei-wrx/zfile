package com.pei.zfile.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import com.fasterxml.jackson.annotation.JsonAlias;
import lombok.Data;

@Data
public class LoginDTO {

    @NotBlank(message = "用户名不能为空")
    @Size(max = 128, message = "账号长度不能超过128位")
    @JsonAlias("account")
    private String username;

    @NotBlank(message = "密码不能为空")
    @Size(max = 72, message = "密码长度不能超过72位")
    private String password;
}
