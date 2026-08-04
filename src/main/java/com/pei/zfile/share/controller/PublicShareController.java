package com.pei.zfile.share.controller;

import com.pei.zfile.common.response.Result;
import com.pei.zfile.file.dto.FileResource;
import com.pei.zfile.file.dto.NodeResponse;
import com.pei.zfile.file.util.FileDownloadHelper;
import com.pei.zfile.share.dto.PublicShareResponse;
import com.pei.zfile.share.dto.ShareVerifyRequest;
import com.pei.zfile.share.dto.ShareVerifyResponse;
import com.pei.zfile.share.service.ShareService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/public/shares")
public class PublicShareController {

    @Autowired
    private ShareService shareService;

    /**
     * 获取分享公开信息
     */
    @GetMapping("/{shareCode}")
    public Result<PublicShareResponse> getShareDetail(@PathVariable String shareCode) {
        PublicShareResponse shareResponse = shareService.getPublicShareDetail(shareCode);
        return Result.success(shareResponse);
    }
    /**
     * 验证分享口令
     */
    @PostMapping("/{shareCode}/verify")
    public Result<ShareVerifyResponse> verifyShareCode(@PathVariable String shareCode,
                                                        @RequestBody @Valid ShareVerifyRequest request) {
        ShareVerifyResponse verifyResponse = shareService.verifySharePassword(shareCode, request);
        return Result.success(verifyResponse);
    }
    /**
     * 浏览分享内容
     */
    @GetMapping("/{shareCode}/nodes")
    public Result<List<NodeResponse>> browseShareNodes(@PathVariable String shareCode,
                                                        @RequestParam(required = false) Long parentId,
                                                        @RequestHeader(value = "X-Share-Token", required = false) String shareToken) {
        List<NodeResponse> nodes = shareService.browseShareNodes(shareCode, parentId, shareToken);
        return Result.success(nodes);
    }
    /**
     * 下载分享中的文件
     */
    @GetMapping("/{shareCode}/files/{fileId}/content")
    public ResponseEntity<InputStreamResource> downloadShareFile(@PathVariable String shareCode,
                                                                  @PathVariable Long fileId,
                                                                  @RequestHeader(value = "X-Share-Token", required = false) String shareToken,
                                                                  HttpServletRequest request) {
        FileResource resource = shareService.downloadShareFile(shareCode, fileId, shareToken);
        return FileDownloadHelper.buildResponse(resource, request, false);
    }
}