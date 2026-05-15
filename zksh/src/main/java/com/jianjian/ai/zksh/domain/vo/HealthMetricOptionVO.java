package com.jianjian.ai.zksh.domain.vo;

import java.util.List;

public record HealthMetricOptionVO(
        String code,
        String name,
        String scene,
        List<String> units
) {
}
