package com.pei.zfile.share.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.Instant;
import java.util.List;

@Data
public class CreateShareRequest {

    @NotEmpty
    @Size(min = 1, max = 100)
    private List<Long> nodeIds;

    @NotBlank
    @Size(min = 1, max = 128)
    private String title;

    @Size(min = 4, max = 32)
    private String password;

    @Future(message = "过期时间必须晚于当前时间")
    private Instant expiresAt;

    @Min(value = 1, message = "下载次数上限至少为1")
    private Integer downloadLimit;
}
