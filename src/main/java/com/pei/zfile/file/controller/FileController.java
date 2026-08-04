package com.pei.zfile.file.controller;

import com.pei.zfile.common.response.Result;
import com.pei.zfile.file.dto.FileResource;
import com.pei.zfile.file.dto.NodeResponse;
import com.pei.zfile.file.dto.UploadFileRequest;
import com.pei.zfile.file.service.FileService;
import com.pei.zfile.file.util.FileDownloadHelper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Slf4j
@Validated
@RestController
@RequestMapping("/files")
public class FileController {

    @Autowired
    private FileService fileService;

    /**
     * 上传单个文件
     */
    @PostMapping("/upload")
    @ResponseStatus(HttpStatus.CREATED)
    public Result<NodeResponse> upload(@Valid UploadFileRequest request) {
        Long userId = Long.valueOf(SecurityContextHolder.getContext().getAuthentication().getPrincipal().toString());
        NodeResponse nodeResponse = fileService.uploadFile(userId, request);
        return Result.success(nodeResponse);
    }
    /**
     * 下载文件，支持 Range 断点续传
     */
    @GetMapping("/{fileId}/content")
    public ResponseEntity<InputStreamResource> download(@PathVariable Long fileId, HttpServletRequest request) {
        Long userId = Long.valueOf(SecurityContextHolder.getContext().getAuthentication().getPrincipal().toString());
        FileResource resource = fileService.downloadFile(userId, fileId);
        return FileDownloadHelper.buildResponse(resource, request, false);
    }

    /**
     * 内联预览文件，浏览器直接渲染而非下载
     */
    @GetMapping("/{fileId}/preview")
    public ResponseEntity<InputStreamResource> preview(@PathVariable Long fileId, HttpServletRequest request) {
        Long userId = Long.valueOf(SecurityContextHolder.getContext().getAuthentication().getPrincipal().toString());
        FileResource resource = fileService.downloadFile(userId, fileId);
        return FileDownloadHelper.buildResponse(resource, request, true);
    }
}