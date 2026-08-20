package com.pei.zfile.file.service;

import com.pei.zfile.file.entity.FileObject;
import com.pei.zfile.storage.model.StoreResult;

import java.util.Collection;
import java.util.List;

public interface FileObjectService {

    FileObject getRequired(Long fileObjectId);

    FileObject findByChecksumAndSize(String checksum, Long sizeBytes);

    FileObject createOrRetain(StoreResult storeResult, String contentType);

    void retain(Long fileObjectId);

    List<FileObject> releaseReferences(Collection<Long> fileObjectIds);
}
