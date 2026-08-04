package com.pei.zfile.file.dto;

public enum ConflictPolicyEnum {
    REJECT,
    RENAME,
    REPLACE;

    public static ConflictPolicyEnum fromValue(String value) {
        for (ConflictPolicyEnum policy : values()) {
            if (policy.name().equalsIgnoreCase(value)) {
                return policy;
            }
        }
        throw new IllegalArgumentException("不支持的冲突策略: " + value + "，可选值: REJECT, RENAME, REPLACE");
    }
}