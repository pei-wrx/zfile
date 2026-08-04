package com.pei.zfile.file.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.springframework.web.multipart.MultipartFile;

@Data
public class UploadFileRequest {

    private Long parentId;

    @NotBlank(message = "冲突策略不能为空")
    private String conflictPolicy;

    @NotNull(message = "文件不能为空")
    private MultipartFile file;
}