package com.pei.zfile.file.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class UploadCheckRequest {

    private Long parentId;

    @NotBlank(message = "文件名不能为空")
    @Size(max = 255, message = "文件名长度不能超过 255 个字符")
    private String name;

    @NotNull(message = "文件大小不能为空")
    @Min(value = 0, message = "文件大小不能为负数")
    private Long sizeBytes;

    @NotBlank(message = "SHA-256 不能为空")
    @Pattern(regexp = "[0-9a-fA-F]{64}", message = "SHA-256 格式不正确")
    private String sha256;

    @NotBlank(message = "冲突策略不能为空")
    @Pattern(regexp = "REJECT|RENAME", message = "冲突策略只能是 REJECT 或 RENAME")
    private String conflictPolicy;
}
