package com.pei.zfile.share.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class CreateShareRequest {

    @NotEmpty
    @Size(min = 1, max = 100)
    private List<Long> nodeIds;

    @NotEmpty
    @Size(min = 1, max = 128)
    private String title;

    @Size(min = 4, max = 32)
    private String password;

    private LocalDateTime expiresAt;

    private Integer downloadLimit;
}