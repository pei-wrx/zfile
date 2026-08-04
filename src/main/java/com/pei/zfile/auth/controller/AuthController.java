package com.pei.zfile.auth.controller;

import com.pei.zfile.auth.dto.LoginDTO;
import com.pei.zfile.auth.dto.RefreshDTO;
import com.pei.zfile.auth.dto.RegisterDTO;
import com.pei.zfile.auth.dto.TokenResponse;
import com.pei.zfile.auth.service.AuthService;
import com.pei.zfile.common.response.Result;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.HttpStatus;
import jakarta.servlet.http.HttpServletRequest;
import com.pei.zfile.user.dto.UserResponse;

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
    public Result<TokenResponse> login(@RequestBody @Valid LoginDTO loginDTO,
                                       HttpServletRequest request) {
        TokenResponse tokenResponse = authService.login(loginDTO, request.getRemoteAddr());
        return Result.success(tokenResponse);
    }
    /**
     * 用户注册
     * @return
     */
    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public Result<UserResponse> register(@RequestBody @Valid RegisterDTO registerDTO) {
        return Result.success(authService.register(registerDTO));
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
    public Result<Void> logout(@RequestHeader("Authorization") String authHeader,
                               @RequestBody @Valid RefreshDTO refreshDTO) {
        String accessToken = authHeader.substring("Bearer ".length());
        authService.logout(accessToken, refreshDTO.getRefreshToken());
        return Result.success();
    }
}
