package com.jianjian.ai.zksh.service.impl;

import com.jianjian.ai.zksh.common.BizException;
import com.jianjian.ai.zksh.domain.entity.UserEntity;
import com.jianjian.ai.zksh.domain.dto.LoginDTO;
import com.jianjian.ai.zksh.domain.dto.RegisterDTO;
import com.jianjian.ai.zksh.domain.dto.UpdateUserProfileDTO;
import com.jianjian.ai.zksh.mapper.UserMapper;
import com.jianjian.ai.zksh.security.JwtTokenService;
import com.jianjian.ai.zksh.service.UserService;
import com.jianjian.ai.zksh.domain.vo.LoginVO;
import com.jianjian.ai.zksh.domain.vo.UserProfileVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Random;

@Service
public class UserServiceImpl implements UserService {

    @Autowired
    private UserMapper userMapper;
    @Autowired
    private StringRedisTemplate redisTemplate;
    @Autowired
    private JwtTokenService jwtTokenService;
    private final Random random = new Random();

    @Value("${app.login.code-prefix}")
    private String codePrefix;

    @Value("${app.login.code-expire-minutes}")
    private long codeExpireMinutes;

    @Override
    public void sendLoginCode(String phone) {
        String code = String.format("%06d", random.nextInt(1_000_000));
        redisTemplate.opsForValue().set(codePrefix + phone, code, Duration.ofMinutes(codeExpireMinutes));
        System.out.println("[MockSMS] phone=" + phone + ", code=" + code);
    }

    @Override
    public LoginVO login(LoginDTO dto) {
        String redisCode = redisTemplate.opsForValue().get(codePrefix + dto.phone());
        if (redisCode == null || !redisCode.equals(dto.code())) {
            throw new BizException("验证码错误或已过期");
        }
        redisTemplate.delete(codePrefix + dto.phone());

        UserEntity user = userMapper.selectByPhone(dto.phone());
        if (user == null) {
            user = new UserEntity();
            user.setPhone(dto.phone());
            user.setNickname("用户" + dto.phone().substring(dto.phone().length() - 4));
            userMapper.insert(user);
        }

        String token = jwtTokenService.generateToken(user.getId(), user.getPhone());
        return new LoginVO(user.getId(), user.getPhone(), token);
    }

    @Override
    public LoginVO register(RegisterDTO dto) {
        String redisCode = redisTemplate.opsForValue().get(codePrefix + dto.phone());
        if (redisCode == null || !redisCode.equals(dto.code())) {
            throw new BizException("验证码错误或已过期");
        }
        if (userMapper.existsByPhone(dto.phone()) > 0) {
            throw new BizException("该手机号已注册");
        }
        UserEntity user = new UserEntity();
        user.setPhone(dto.phone());
        user.setNickname((dto.nickname() == null || dto.nickname().isBlank())
                ? "用户" + dto.phone().substring(dto.phone().length() - 4)
                : dto.nickname());
        userMapper.insert(user);
        redisTemplate.delete(codePrefix + dto.phone());
        String token = jwtTokenService.generateToken(user.getId(), user.getPhone());
        return new LoginVO(user.getId(), user.getPhone(), token);
    }

    @Override
    public UserProfileVO getProfile(Long userId) {
        UserEntity user = userMapper.selectById(userId);
        if (user == null) {
            throw new BizException("用户不存在");
        }
        return toVO(user);
    }

    @Override
    public UserProfileVO updateProfile(Long userId, UpdateUserProfileDTO dto) {
        UserEntity exist = userMapper.selectById(userId);
        if (exist == null) {
            throw new BizException("用户不存在");
        }
        UserEntity user = new UserEntity();
        user.setId(userId);
        user.setNickname(dto.nickname());
        user.setGender(dto.gender());
        user.setAge(dto.age());
        user.setHeight(dto.height());
        user.setWeight(dto.weight());
        userMapper.updateProfile(user);
        UserEntity updated = userMapper.selectById(userId);
        return toVO(updated);
    }

    private UserProfileVO toVO(UserEntity user) {
        return new UserProfileVO(
                user.getId(),
                user.getPhone(),
                user.getNickname(),
                user.getGender(),
                user.getAge(),
                user.getHeight(),
                user.getWeight()
        );
    }
}
