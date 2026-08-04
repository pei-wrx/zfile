package com.pei.zfile.storage.model;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class StoreResult {

    private String tempKey;

    private String sha256;

    private long sizeBytes;
}