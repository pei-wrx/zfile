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
import org.springframework.validation.annotation.Validated;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;

@Slf4j
@Validated
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
    public Result<PageResult<ShareResponse>> list(
            @RequestParam(required = false)
            @Pattern(regexp = "ACTIVE|CANCELLED|EXPIRED", message = "分享状态不正确") String status,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
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
