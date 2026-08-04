package com.pei.zfile.user.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserResponse {

    private Long id;

    private String username;

    private String email;

    private String role;

    private String status;

    private Long quotaBytes;

    private Long usedBytes;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}