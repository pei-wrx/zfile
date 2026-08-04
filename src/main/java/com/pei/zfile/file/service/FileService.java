package com.pei.zfile.file.service;

import com.pei.zfile.file.dto.FileResource;
import com.pei.zfile.file.dto.NodeResponse;
import com.pei.zfile.file.dto.UploadFileRequest;

public interface FileService {

    NodeResponse uploadFile(Long userId, UploadFileRequest request);

    FileResource downloadFile(Long userId, Long fileId);
}