package com.pei.zfile.audit.service;

import com.pei.zfile.audit.constant.AuditEnum;

public interface AuditService {

    void record(Long operationId, AuditEnum action, String targetType, Long targetId, String detailJson, String ip);
}
