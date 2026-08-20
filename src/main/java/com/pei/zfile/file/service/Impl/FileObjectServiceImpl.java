package com.pei.zfile.file.service.Impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.pei.zfile.common.exception.BusinessException;
import com.pei.zfile.common.response.ResultCode;
import com.pei.zfile.file.entity.FileObject;
import com.pei.zfile.file.mapper.FileObjectMapper;
import com.pei.zfile.file.service.FileObjectService;
import com.pei.zfile.storage.model.StoreResult;
import com.pei.zfile.storage.service.StorageService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
public class FileObjectServiceImpl implements FileObjectService {

    @Autowired
    private FileObjectMapper fileObjectMapper;

    @Autowired
    private StorageService storageService;

    @Override
    public FileObject getRequired(Long fileObjectId) {
        FileObject fileObject = fileObjectId == null ? null : fileObjectMapper.selectById(fileObjectId);
        if (fileObject == null) {
            throw new BusinessException(ResultCode.FILE_NOT_FOUND, "物理文件对象不存在");
        }
        return fileObject;
    }

    @Override
    public FileObject findByChecksumAndSize(String checksum, Long sizeBytes) {
        if (checksum == null || sizeBytes == null) {
            return null;
        }
        return fileObjectMapper.selectOne(new LambdaQueryWrapper<FileObject>()
                .eq(FileObject::getChecksum, checksum.toLowerCase(Locale.ROOT))
                .eq(FileObject::getSizeBytes, sizeBytes)
                .last("LIMIT 1"));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public FileObject createOrRetain(StoreResult storeResult, String contentType) {
        FileObject existing = findByChecksumAndSize(storeResult.getSha256(), storeResult.getSizeBytes());
        if (existing != null) {
            storageService.deleteTemp(storeResult.getTempKey());
            retain(existing.getId());
            return existing;
        }

        String storageKey = UUID.randomUUID().toString().replace("-", "");
        try {
            storageService.commitTemp(storeResult.getTempKey(), storageKey);
        } catch (RuntimeException e) {
            storageService.deleteTemp(storeResult.getTempKey());
            throw e;
        }
        registerRollbackCleanup(storageKey);

        FileObject candidate = new FileObject()
                .setChecksum(storeResult.getSha256().toLowerCase(Locale.ROOT))
                .setSizeBytes(storeResult.getSizeBytes())
                .setContentType(contentType)
                .setStorageKey(storageKey)
                .setReferenceCount(1L);
        int inserted = fileObjectMapper.insertIgnore(candidate);
        if (inserted > 0) {
            return candidate;
        }

        storageService.delete(storageKey);
        FileObject raced = fileObjectMapper.selectOne(new LambdaQueryWrapper<FileObject>()
                .eq(FileObject::getChecksum, candidate.getChecksum())
                .last("LIMIT 1"));
        if (raced == null || storeResult.getSizeBytes() != raced.getSizeBytes()) {
            throw new BusinessException(ResultCode.STORAGE_ERROR, "无法建立共享物理文件对象");
        }
        retain(raced.getId());
        return raced;
    }

    @Override
    public void retain(Long fileObjectId) {
        if (fileObjectId == null || fileObjectMapper.retain(fileObjectId) == 0) {
            throw new BusinessException(ResultCode.FILE_NOT_FOUND, "物理文件对象不存在");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<FileObject> releaseReferences(Collection<Long> fileObjectIds) {
        Map<Long, Long> counts = new LinkedHashMap<>();
        for (Long fileObjectId : fileObjectIds) {
            if (fileObjectId != null) {
                counts.merge(fileObjectId, 1L, Long::sum);
            }
        }

        List<FileObject> released = new ArrayList<>();
        for (Map.Entry<Long, Long> entry : counts.entrySet()) {
            if (fileObjectMapper.release(entry.getKey(), entry.getValue()) == 0) {
                throw new BusinessException(ResultCode.FILE_NOT_FOUND, "物理文件对象引用计数不足");
            }
            FileObject fileObject = fileObjectMapper.selectById(entry.getKey());
            if (fileObject == null) {
                throw new BusinessException(ResultCode.FILE_NOT_FOUND, "物理文件对象不存在");
            }
            if (fileObject.getReferenceCount() == 0) {
                fileObjectMapper.deleteById(fileObject.getId());
                released.add(fileObject);
            }
        }
        return released;
    }

    private void registerRollbackCleanup(String storageKey) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status != STATUS_COMMITTED) {
                    storageService.delete(storageKey);
                }
            }
        });
    }
}
