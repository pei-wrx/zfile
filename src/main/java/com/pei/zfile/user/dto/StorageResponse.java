package com.pei.zfile.user.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StorageResponse {

    private Long quotaBytes;

    private Long usedBytes;

    private Long availableBytes;

    private Double usageRatio;
}