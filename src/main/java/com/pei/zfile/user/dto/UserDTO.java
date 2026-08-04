package com.pei.zfile.user.dto;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class UserDTO {

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