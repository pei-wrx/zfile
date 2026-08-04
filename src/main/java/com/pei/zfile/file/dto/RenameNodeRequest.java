package com.pei.zfile.file.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class RenameNodeRequest {

    @NotBlank
    @Size(min = 1, max = 255)
    @Pattern(regexp = "^(?![.]{1,2}$)(?!.*[\\\\/:*?\"<>|]).+$",
            message = "名称不能为 . 或 ..，且不能包含 \\ / : * ? \" < > | 等特殊字符")
    private String name;
}