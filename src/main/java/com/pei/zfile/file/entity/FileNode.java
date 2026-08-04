package com.pei.zfile.file.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("file_nodes")
public class FileNode {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long ownerId;

    private Long parentId;

    private String nodeType;

    private String name;

    private Long sizeBytes;

    private String contentType;

    private String storageKey;

    private String checksum;

    private String status;

    private Long originalParentId;

    private LocalDateTime deletedAt;

    @Version
    private Integer version;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}