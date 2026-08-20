package com.pei.zfile.file.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.pei.zfile.file.entity.FileNode;
import com.pei.zfile.file.entity.FileObject;
import com.pei.zfile.file.mapper.FileNodeMapper;
import com.pei.zfile.file.mapper.FileObjectMapper;
import com.pei.zfile.storage.service.StorageService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Removes historical duplicate blobs after V2 has migrated their node references. */
@Slf4j
@Component
public class FileObjectMigrationCleanup {

    private final FileNodeMapper fileNodeMapper;
    private final FileObjectMapper fileObjectMapper;
    private final StorageService storageService;

    public FileObjectMigrationCleanup(FileNodeMapper fileNodeMapper,
                                      FileObjectMapper fileObjectMapper,
                                      StorageService storageService) {
        this.fileNodeMapper = fileNodeMapper;
        this.fileObjectMapper = fileObjectMapper;
        this.storageService = storageService;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void removeMigratedDuplicateBlobs() {
        try {
            List<FileObject> objects = fileObjectMapper.selectList(null);
            if (objects == null || objects.isEmpty()) {
                return;
            }
            Set<String> liveKeys = objects.stream()
                    .map(FileObject::getStorageKey)
                    .filter(java.util.Objects::nonNull)
                    .collect(java.util.stream.Collectors.toSet());
            Set<String> legacyKeys = new HashSet<>(fileNodeMapper.selectList(
                    new LambdaQueryWrapper<FileNode>()
                            .select(FileNode::getStorageKey)
                            .eq(FileNode::getNodeType, "FILE")
                            .isNotNull(FileNode::getFileObjectId)
                            .isNotNull(FileNode::getStorageKey))
                    .stream()
                    .map(FileNode::getStorageKey)
                    .toList());
            legacyKeys.removeAll(liveKeys);
            legacyKeys.forEach(storageService::delete);
            if (!legacyKeys.isEmpty()) {
                log.info("Removed {} migrated duplicate physical files", legacyKeys.size());
            }
        } catch (RuntimeException e) {
            log.warn("Skipped migrated duplicate cleanup; it will be retried on the next startup", e);
        }
    }
}
