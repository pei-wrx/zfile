package com.pei.zfile.admin.controller;

import com.pei.zfile.admin.dto.UpdateQuotaRequest;
import com.pei.zfile.admin.dto.UpdateUserStatusRequest;
import com.pei.zfile.admin.dto.UserPageRequest;
import com.pei.zfile.admin.service.AdminUserService;
import com.pei.zfile.common.response.PageResult;
import com.pei.zfile.common.response.Result;
import com.pei.zfile.user.dto.StorageResponse;
import com.pei.zfile.user.dto.UserResponse;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/admin/users")
public class AdminUserController {

    @Autowired
    private AdminUserService adminUserService;

    @GetMapping
    public Result<PageResult<UserResponse>> selectUsers(@Valid UserPageRequest request) {
        PageResult<UserResponse> pageResult = adminUserService.selectUsers(request);
        return Result.success(pageResult);
    }

    @PatchMapping("/{userId}/status")
    public Result<UserResponse> updateUserStatus(@PathVariable Long userId,
                                                 @RequestBody @Valid UpdateUserStatusRequest request) {
        UserResponse userResponse = adminUserService.startOrDisableUser(userId, request);
        return Result.success(userResponse);
    }

    @PatchMapping("/{userId}/quota")
    public Result<StorageResponse> updateQuota(@PathVariable Long userId,
                                               @RequestBody @Valid UpdateQuotaRequest request) {
        StorageResponse storageResponse = adminUserService.updateUserQuota(userId, request);
        return Result.success(storageResponse);
    }
}