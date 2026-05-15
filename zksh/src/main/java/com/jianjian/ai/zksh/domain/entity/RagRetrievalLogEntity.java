package com.jianjian.ai.zksh.domain.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class RagRetrievalLogEntity {
    private Long id;
    private Long userId;
    private Long sessionId;
    private String queryText;
    private Integer recallCount;
    private Integer topkCount;
    private Integer latencyMs;
    private String resultSnapshotJson;
    private LocalDateTime createdAt;
}

