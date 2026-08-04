package com.pei.zfile.share.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ShareVerifyRequest {

    @Size(min = 4, max = 32)
    private String password;
}
