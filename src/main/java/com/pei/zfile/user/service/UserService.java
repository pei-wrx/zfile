package com.pei.zfile.user.service;

import com.pei.zfile.user.dto.UpdateProfileDTO;
import com.pei.zfile.user.dto.UpdatePwdDTO;
import com.pei.zfile.user.entity.User;

public interface UserService {

    User getById(Long userId);

    void updateProfile(Long userId, UpdateProfileDTO updateProfileDTO);

    void updatePassword(Long userId, UpdatePwdDTO updatePwdDTO);
}