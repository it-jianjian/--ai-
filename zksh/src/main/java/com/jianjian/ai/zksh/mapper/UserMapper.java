package com.jianjian.ai.zksh.mapper;

import com.jianjian.ai.zksh.domain.entity.UserEntity;
import org.apache.ibatis.annotations.Param;

public interface UserMapper {
    UserEntity selectById(@Param("id") Long id);

    UserEntity selectByPhone(@Param("phone") String phone);

    int existsByPhone(@Param("phone") String phone);

    int insert(UserEntity user);

    int updateProfile(UserEntity user);
}
