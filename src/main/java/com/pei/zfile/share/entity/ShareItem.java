package com.pei.zfile.share.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("share_items")
public class ShareItem {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long shareId;

    private Long nodeId;
}