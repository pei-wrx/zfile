package com.pei.zfile.storage.service;

import com.pei.zfile.storage.model.StoreResult;

import java.io.InputStream;

public interface StorageService {

    StoreResult storeTemp(InputStream inputStream);

    void commitTemp(String tempKey, String storageKey);

    void deleteTemp(String tempKey);

    void delete(String storageKey);

    void copy(String sourceStorageKey, String targetStorageKey);

    InputStream load(String storageKey);
}