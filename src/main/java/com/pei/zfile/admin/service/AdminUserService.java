package com.pei.zfile.admin.service;

import com.pei.zfile.admin.dto.UpdateQuotaRequest;
import com.pei.zfile.admin.dto.UpdateUserStatusRequest;
import com.pei.zfile.admin.dto.UserPageRequest;
import com.pei.zfile.common.response.PageResult;
import com.pei.zfile.user.dto.StorageResponse;
import com.pei.zfile.user.dto.UserResponse;
import jakarta.validation.Valid;

public interface AdminUserService {

    PageResult<UserResponse> selectUsers(@Valid UserPageRequest request);

    UserResponse startOrDisableUser(Long userId, @Valid UpdateUserStatusRequest request);

    StorageResponse updateUserQuota(Long userId, @Valid UpdateQuotaRequest request);
}