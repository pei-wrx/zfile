package com.pei.zfile.user.service.Impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.pei.zfile.common.exception.BusinessException;
import com.pei.zfile.common.response.ResultCode;
import com.pei.zfile.user.dto.UpdateProfileDTO;
import com.pei.zfile.user.dto.UpdatePwdDTO;
import com.pei.zfile.user.entity.User;
import com.pei.zfile.user.mapper.UserMapper;
import com.pei.zfile.user.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.data.redis.core.StringRedisTemplate;

import static com.pei.zfile.common.util.RedisConstant.REFRESH_TOKEN_KEY;

@Service
public class UserServiceImpl implements UserService {
    @Autowired
    private UserMapper userMapper;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public User getById(Long userId) {
        return userMapper.selectById(userId);
    }

    @Override
    public void updateProfile(Long userId, UpdateProfileDTO updateProfileDTO) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ResultCode.USER_NOT_FOUND);
        }

        boolean changed = false;

        if (updateProfileDTO.getUsername() != null
                && !updateProfileDTO.getUsername().equals(user.getUsername())) {
            Long count = userMapper.selectCount(
                    new LambdaQueryWrapper<User>()
                            .eq(User::getUsername, updateProfileDTO.getUsername())
                            .ne(User::getId, userId)
            );
            if (count > 0) {
                throw new BusinessException(ResultCode.USERNAME_EXISTS);
            }
            user.setUsername(updateProfileDTO.getUsername());
            changed = true;
        }

        if (updateProfileDTO.getEmail() != null
                && !updateProfileDTO.getEmail().equals(user.getEmail())) {
            Long count = userMapper.selectCount(
                    new LambdaQueryWrapper<User>()
                            .eq(User::getEmail, updateProfileDTO.getEmail())
                            .ne(User::getId, userId)
            );
            if (count > 0) {
                throw new BusinessException(ResultCode.EMAIL_EXISTS);
            }
            user.setEmail(updateProfileDTO.getEmail());
            changed = true;
        }

        if (changed) {
            userMapper.updateById(user);
        }
    }

    @Override
    public void updatePassword(Long userId, UpdatePwdDTO updatePwdDTO) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ResultCode.USER_NOT_FOUND);
        }

        if(!passwordEncoder.matches(updatePwdDTO.getCurrentPassword(),user.getPasswordHash())){
            throw new BusinessException(ResultCode.INVALID_CREDENTIALS, "当前密码错误");
        }
        user.setPasswordHash(passwordEncoder.encode(updatePwdDTO.getNewPassword()));
        userMapper.updateById(user);
        stringRedisTemplate.delete(REFRESH_TOKEN_KEY + userId);
    }
}
