package com.pei.zfile.auth.service;

import com.pei.zfile.auth.dto.LoginDTO;
import com.pei.zfile.auth.dto.RefreshDTO;
import com.pei.zfile.auth.dto.RegisterDTO;
import com.pei.zfile.auth.dto.TokenResponse;

public interface AuthService {

    TokenResponse login(LoginDTO loginDTO);

    void register(RegisterDTO registerDTO);

    TokenResponse refresh(RefreshDTO refreshDTO);

    void logout(String token);
}