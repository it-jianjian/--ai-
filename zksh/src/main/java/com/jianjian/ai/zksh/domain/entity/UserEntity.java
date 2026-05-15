package com.jianjian.ai.zksh.domain.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class UserEntity {
    private Long id;
    private String phone;
    private String nickname;
    private String gender;
    private Integer age;
    private Double height;
    private Double weight;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
