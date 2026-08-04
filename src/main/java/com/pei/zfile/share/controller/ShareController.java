package com.pei.zfile.share.controller;

import com.pei.zfile.common.response.PageResult;
import com.pei.zfile.common.response.Result;
import com.pei.zfile.share.dto.CreateShareRequest;
import com.pei.zfile.share.dto.ShareResponse;
import com.pei.zfile.share.service.ShareService;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/shares")
public class ShareController {

    @Autowired
    private ShareService shareService;

    /**
     * 创建分享
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Result<ShareResponse> create(@RequestBody @Valid CreateShareRequest request) {
        Long userId = Long.valueOf(SecurityContextHolder.getContext().getAuthentication().getPrincipal().toString());
        ShareResponse shareResponse = shareService.create(userId, request);
        return Result.success(shareResponse);
    }
    /**
     * 查询我的分享
     */
    @GetMapping
    public Result<PageResult<ShareResponse>> list(@RequestParam(required = false) String status,
                                                   @RequestParam(defaultValue = "1") int page,
                                                   @RequestParam(defaultValue = "20") int size) {
        Long userId = Long.valueOf(SecurityContextHolder.getContext().getAuthentication().getPrincipal().toString());
        PageResult<ShareResponse> pageResult = shareService.list(userId, status, page, size);
        return Result.success(pageResult);
    }
    /**
     * 获取自己的分享详情
     */
    @GetMapping("/{shareId}")
    public Result<ShareResponse> detail(@PathVariable Long shareId) {
        Long userId = Long.valueOf(SecurityContextHolder.getContext().getAuthentication().getPrincipal().toString());
        ShareResponse shareResponse = shareService.detail(userId, shareId);
        return Result.success(shareResponse);
    }
    /**
     * 取消分享
     */
    @DeleteMapping("/{shareId}")
    public Result<Void> cancel(@PathVariable Long shareId) {
        Long userId = Long.valueOf(SecurityContextHolder.getContext().getAuthentication().getPrincipal().toString());
        shareService.cancel(userId, shareId);
        return Result.success();
    }
}