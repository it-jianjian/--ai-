package com.jianjian.ai.zksh.domain.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

public record UpdateUserProfileDTO(
        @Size(max = 50, message = "昵称长度不能超过50")
        String nickname,
        @Size(max = 10, message = "性别长度不能超过10")
        String gender,
        @Min(value = 0, message = "年龄不能小于0")
        @Max(value = 150, message = "年龄不能大于150")
        Integer age,
        Double height,
        Double weight
) {
}
