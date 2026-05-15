package com.jianjian.ai.zksh.domain.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class KnowledgeChunkEntity {
    private Long id;
    private Long docId;
    private Integer chunkIndex;
    private String content;
    private Integer tokenCount;
    private String qdrantPointId;
    private String metadataJson;
    private LocalDateTime createdAt;
}

