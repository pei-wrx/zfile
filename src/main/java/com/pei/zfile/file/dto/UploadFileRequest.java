package com.pei.zfile.file.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.springframework.web.multipart.MultipartFile;

@Data
public class UploadFileRequest {

    private Long parentId;

    @NotBlank(message = "冲突策略不能为空")
    @Pattern(regexp = "REJECT|RENAME", message = "冲突策略只能是 REJECT 或 RENAME")
    private String conflictPolicy;

    @Size(min = 64, max = 64, message = "SHA-256 格式不正确")
    @Pattern(regexp = "[0-9a-fA-F]{64}", message = "SHA-256 格式不正确")
    private String sha256;

    @NotNull(message = "文件不能为空")
    private MultipartFile file;
}
