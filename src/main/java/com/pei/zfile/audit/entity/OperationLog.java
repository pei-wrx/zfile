package com.pei.zfile.audit.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.pei.zfile.audit.constant.AuditEnum;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;
import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper=false)
@Accessors(chain=true)
@TableName("operation_logs")
public class OperationLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long operatorId;

    private AuditEnum action;

    private String targetType;

    private Long targetId;

    private String detailJson;

    private String ip;

    private LocalDateTime createAt;
}
