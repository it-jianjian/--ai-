package com.jianjian.ai.zksh.service;

import com.jianjian.ai.zksh.domain.dto.LoginDTO;
import com.jianjian.ai.zksh.domain.dto.RegisterDTO;
import com.jianjian.ai.zksh.domain.dto.UpdateUserProfileDTO;
import com.jianjian.ai.zksh.domain.vo.LoginVO;
import com.jianjian.ai.zksh.domain.vo.UserProfileVO;

public interface UserService {
    void sendLoginCode(String phone);

    LoginVO login(LoginDTO dto);

    LoginVO register(RegisterDTO dto);

    UserProfileVO getProfile(Long userId);

    UserProfileVO updateProfile(Long userId, UpdateUserProfileDTO dto);
}
