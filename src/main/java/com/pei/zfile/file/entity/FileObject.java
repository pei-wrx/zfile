package com.pei.zfile.file.entity;

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
@TableName("file_objects")
public class FileObject {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String checksum;

    private Long sizeBytes;

    private String contentType;

    private String storageKey;

    private Long referenceCount;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
