package com.pei.zfile.admin.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class UserPageRequest {

    @Size(max = 100)
    private String keyword;

    @Pattern(regexp = "ACTIVE|DISABLED", message = "用户状态只能是 ACTIVE 或 DISABLED")
    private String status;

    @Min(1)
    private Integer page;

    @Min(1)
    @Max(100)
    private Integer size;
}
