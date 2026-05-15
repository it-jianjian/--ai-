package com.jianjian.ai.zksh.domain.vo;

public record UserProfileVO(
        Long id,
        String phone,
        String nickname,
        String gender,
        Integer age,
        Double height,
        Double weight
) {
}
