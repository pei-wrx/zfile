package com.pei.zfile.file.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class NodeDetailResponse {

    private Long id;

    private Long parentId;

    private String type;

    private String name;

    private Long sizeBytes;

    private String contentType;

    private String checksum;

    private String status;

    private LocalDateTime deletedAt;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    private List<BreadcrumbItem> breadcrumbs;
}