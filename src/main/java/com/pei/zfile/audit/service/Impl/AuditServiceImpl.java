package com.pei.zfile.audit.service.Impl;

import com.pei.zfile.audit.constant.AuditEnum;
import com.pei.zfile.audit.entity.OperationLog;
import com.pei.zfile.audit.mapper.AuditMapper;
import com.pei.zfile.audit.service.AuditService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class AuditServiceImpl implements AuditService {

    @Autowired
    private AuditMapper auditMapper;

    @Async("auditExecutor")
    @Override
    public void record(Long operationId, AuditEnum action, String targetType, Long targetId, String detailJson, String ip) {
        OperationLog operationLog = new OperationLog()
                .setOperatorId(operationId)
                .setAction(action)
                .setTargetType(targetType)
                .setTargetId(targetId)
                .setDetailJson(detailJson)
                .setIp(ip);
        auditMapper.insert(operationLog);
    }
}
