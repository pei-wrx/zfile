package com.pei.zfile.file.controller;

import com.pei.zfile.common.response.Result;
import com.pei.zfile.file.dto.CreateFolderRequest;
import com.pei.zfile.file.dto.NodeResponse;
import com.pei.zfile.file.service.NodeService;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/folders")
public class FolderController {

    @Autowired
    private NodeService nodeService;

    /**
     * 新建目录
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Result<NodeResponse> createFolder(@RequestBody @Valid CreateFolderRequest request) {
        Long userId = Long.valueOf(SecurityContextHolder.getContext().getAuthentication().getPrincipal().toString());
        NodeResponse folder = nodeService.createFolder(userId, request);
        return Result.success(folder);
    }
}