package com.pei.zfile.auth.controller;

import com.pei.zfile.auth.dto.LoginDTO;
import com.pei.zfile.auth.dto.RefreshDTO;
import com.pei.zfile.auth.dto.RegisterDTO;
import com.pei.zfile.auth.dto.TokenResponse;
import com.pei.zfile.auth.service.AuthService;
import com.pei.zfile.common.response.Result;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/auth")
public class AuthController {

    @Autowired
    private AuthService authService;

    /**
     * 登录
     * @return
     */
    @PostMapping("/login")
    public Result<TokenResponse> login(@RequestBody @Valid LoginDTO loginDTO) {
        TokenResponse tokenResponse = authService.login(loginDTO);
        return Result.success(tokenResponse);
    }
    /**
     * 用户注册
     * @return
     */
    @PostMapping("/register")
    public Result<Void> register(@RequestBody @Valid RegisterDTO registerDTO) {
        authService.register(registerDTO);
        return Result.success();
    }
    /**
     * 刷新token
     * @return
     */
    @PostMapping("/refresh")
    public Result<TokenResponse> refresh(@RequestBody @Valid RefreshDTO refreshDTO) {
        TokenResponse tokenResponse = authService.refresh(refreshDTO);
        return Result.success(tokenResponse);
    }
    /**
     * 退出登录
     * @return
     */
    @PostMapping("/logout")
    public Result<Void> logout(@RequestHeader("Authorization") String authHeader) {
        log.info("进入 logout, authHeader={}", authHeader);
        String token = authHeader.replace("Bearer ", "");
        authService.logout(token);
        return Result.success();
    }
}