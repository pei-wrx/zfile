package com.pei.zfile.audit.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.pei.zfile.audit.entity.OperationLog;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AuditMapper extends BaseMapper<OperationLog> {
}
