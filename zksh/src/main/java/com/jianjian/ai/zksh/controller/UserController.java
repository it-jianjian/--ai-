package com.jianjian.ai.zksh.controller;

import com.jianjian.ai.zksh.common.ApiResponse;
import com.jianjian.ai.zksh.domain.dto.LoginDTO;
import com.jianjian.ai.zksh.domain.dto.RegisterDTO;
import com.jianjian.ai.zksh.domain.dto.SendCodeDTO;
import com.jianjian.ai.zksh.domain.dto.UpdateUserProfileDTO;
import com.jianjian.ai.zksh.security.UserContext;
import com.jianjian.ai.zksh.service.UserService;
import com.jianjian.ai.zksh.domain.vo.LoginVO;
import com.jianjian.ai.zksh.domain.vo.UserProfileVO;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/user")
public class UserController {

    @Autowired
    private UserService userService;

    @PostMapping("/send-code")
    public ApiResponse<Map<String, String>> sendCode(@Valid @RequestBody SendCodeDTO request) {
        userService.sendLoginCode(request.phone());
        return ApiResponse.ok(Map.of("message", "验证码已发送（开发环境请看后端控制台日志）"));
    }

    @PostMapping("/login")
    public ApiResponse<LoginVO> login(@Valid @RequestBody LoginDTO request) {
        return ApiResponse.ok(userService.login(request));
    }

    @PostMapping("/register")
    public ApiResponse<LoginVO> register(@Valid @RequestBody RegisterDTO request) {
        return ApiResponse.ok(userService.register(request));
    }

    @GetMapping("/profile")
    public ApiResponse<UserProfileVO> profile() {
        return ApiResponse.ok(userService.getProfile(UserContext.getUserId()));
    }

    @PutMapping("/profile")
    public ApiResponse<UserProfileVO> updateProfile(@Valid @RequestBody UpdateUserProfileDTO request) {
        return ApiResponse.ok(userService.updateProfile(UserContext.getUserId(), request));
    }
}
