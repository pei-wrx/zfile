package com.pei.zfile.admin.service.Impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.pei.zfile.admin.dto.UpdateQuotaRequest;
import com.pei.zfile.admin.dto.UpdateUserStatusRequest;
import com.pei.zfile.admin.dto.UserPageRequest;
import com.pei.zfile.admin.service.AdminUserService;
import com.pei.zfile.common.exception.BusinessException;
import com.pei.zfile.common.response.PageResult;
import com.pei.zfile.common.response.ResultCode;
import com.pei.zfile.storage.service.StorageService;
import com.pei.zfile.user.dto.StorageResponse;
import com.pei.zfile.user.dto.UserResponse;
import com.pei.zfile.user.entity.User;
import com.pei.zfile.user.mapper.UserMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.data.redis.core.StringRedisTemplate;

import static com.pei.zfile.common.util.RedisConstant.REFRESH_TOKEN_KEY;

import java.util.List;

@Service
public class AdminUserServiceImpl implements AdminUserService {
    @Autowired
    private StorageService storageService;
    @Autowired
    private UserMapper userMapper;
    @Autowired
    private StringRedisTemplate stringRedisTemplate;


    @Override
    public PageResult<UserResponse> selectUsers(UserPageRequest request) {

        int pageNum = request.getPage() != null ? request.getPage() : 1;
        int pageSize = request.getSize() != null ? request.getSize() : 20;

        Page<User> page = new Page<>(pageNum, pageSize);

        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<User>()
                .select(User::getId, User::getUsername, User::getEmail,
                        User::getRole, User::getStatus,
                        User::getQuotaBytes, User::getUsedBytes,
                        User::getCreatedAt, User::getUpdatedAt)
                .and(request.getKeyword() != null, w -> w
                        .like(User::getUsername, request.getKeyword())
                        .or()
                        .like(User::getEmail, request.getKeyword()))
                .eq(request.getStatus() != null, User::getStatus, request.getStatus());

        Page<User> userPage = userMapper.selectPage(page, wrapper);

        List<UserResponse> items = userPage.getRecords().stream()
                .map(u -> UserResponse.builder()
                        .id(u.getId())
                        .username(u.getUsername())
                        .email(u.getEmail())
                        .role(u.getRole())
                        .status(u.getStatus())
                        .quotaBytes(u.getQuotaBytes())
                        .usedBytes(u.getUsedBytes())
                        .createdAt(u.getCreatedAt())
                        .updatedAt(u.getUpdatedAt())
                        .build())
                .toList();

        return PageResult.of(items, pageNum, pageSize, userPage.getTotal());
    }

    @Override
    public UserResponse startOrDisableUser(Long userId, UpdateUserStatusRequest request) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ResultCode.USER_NOT_FOUND);
        }
        user.setStatus(request.getStatus());
        userMapper.updateById(user);
        if ("DISABLED".equals(request.getStatus())) {
            stringRedisTemplate.delete(REFRESH_TOKEN_KEY + userId);
        }

        return UserResponse.builder()
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
    }

    @Override
    public StorageResponse updateUserQuota(Long userId, UpdateQuotaRequest request) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ResultCode.USER_NOT_FOUND);
        }
        user.setQuotaBytes(request.getQuotaBytes());
        userMapper.updateById(user);

        long quotaBytes = user.getQuotaBytes();
        long usedBytes = user.getUsedBytes();
        long availableBytes = Math.max(0, quotaBytes - usedBytes);
        double usageRatio = quotaBytes > 0 ? (double) usedBytes / quotaBytes : 0.0;

        return StorageResponse.builder()
                .quotaBytes(quotaBytes)
                .usedBytes(usedBytes)
                .availableBytes(availableBytes)
                .usageRatio(usageRatio)
                .build();
    }
}
