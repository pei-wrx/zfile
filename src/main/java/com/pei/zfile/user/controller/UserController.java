package com.pei.zfile.user.controller;

import com.pei.zfile.common.exception.BusinessException;
import com.pei.zfile.common.response.Result;
import com.pei.zfile.common.response.ResultCode;
import com.pei.zfile.user.dto.StorageResponse;
import com.pei.zfile.user.dto.UpdateProfileDTO;
import com.pei.zfile.user.dto.UpdatePwdDTO;
import com.pei.zfile.user.dto.UserResponse;
import com.pei.zfile.user.entity.User;
import com.pei.zfile.user.service.UserService;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/users/me")
public class UserController {

    @Autowired
    private UserService userService;


    /**
     * 获取当前用户
     */
    @GetMapping
    public Result<UserResponse> getMe() {
        Long userId = getCurrentUserId();
        User user = userService.getById(userId);
        if (user == null) {
            throw new BusinessException(ResultCode.USER_NOT_FOUND);
        }

        UserResponse userResponse = UserResponse.builder()
                .id(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .role(user.getRole())
                .status(user.getStatus())
                .quotaBytes(user.getQuotaBytes())
                .usedBytes(user.getUsedBytes())
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt())
                .build();
        return Result.success(userResponse);
    }
    /**
     * 修改当前用户资料
     */
    @PatchMapping
    public Result<Void> updateMe(@RequestBody @Valid UpdateProfileDTO updateProfileDTO) {
        userService.updateProfile(getCurrentUserId(), updateProfileDTO);
        return Result.success();
    }

    /**
     * 修改当前用户密码
     */
    @PutMapping("/password")
    public Result<Void> updatePassword(@RequestBody @Valid UpdatePwdDTO updatePwdDTO) {
        userService.updatePassword(getCurrentUserId(), updatePwdDTO);
        return Result.success();
    }
    /**
     * 获取当前用户存储信息 查询配额和用量
     */
    @GetMapping("/storage")
    public Result<StorageResponse> getStorage() {
        Long userId = getCurrentUserId();
        User user = userService.getById(userId);
        if (user == null) {
            throw new BusinessException(ResultCode.USER_NOT_FOUND);
        }

        long quotaBytes = user.getQuotaBytes();
        long usedBytes = user.getUsedBytes();
        long availableBytes = Math.max(0, quotaBytes - usedBytes);
        double usageRatio = quotaBytes > 0 ? (double) usedBytes / quotaBytes : 0.0;

        StorageResponse storageResponse = StorageResponse.builder()
                .quotaBytes(quotaBytes)
                .usedBytes(usedBytes)
                .availableBytes(availableBytes)
                .usageRatio(usageRatio)
                .build();
        return Result.success(storageResponse);
    }

    private Long getCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED);
        }
        return Long.valueOf(auth.getPrincipal().toString());
    }
}