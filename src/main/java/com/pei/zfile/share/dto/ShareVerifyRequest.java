package com.pei.zfile.share.dto;

import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ShareVerifyRequest {

    @NotBlank(message = "分享口令不能为空")
    @Size(max = 32)
    private String password;
}
