package com.pei.zfile.trash.dto;

import lombok.Data;

@Data
public class RestoreNodeRequest {

    private Long targetParentId;

    private String conflictPolicy;
}