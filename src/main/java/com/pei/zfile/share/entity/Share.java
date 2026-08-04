package com.pei.zfile.share.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;
import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("shares")
public class Share {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long ownerId;

    private String shareCode;

    private String title;

    private String passwordHash;

    private LocalDateTime expiresAt;

    private Integer downloadLimit;

    private Integer downloadCount;

    private String status;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}