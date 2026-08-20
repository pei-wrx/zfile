package com.pei.zfile.file.service;

import com.pei.zfile.file.dto.FileResource;
import com.pei.zfile.file.dto.NodeResponse;
import com.pei.zfile.file.dto.UploadFileRequest;
import com.pei.zfile.file.dto.UploadCheckRequest;
import com.pei.zfile.file.dto.UploadCheckResponse;

public interface FileService {

    NodeResponse uploadFile(Long userId, UploadFileRequest request);

    UploadCheckResponse checkUpload(Long userId, UploadCheckRequest request);

    FileResource downloadFile(Long userId, Long fileId);
}
