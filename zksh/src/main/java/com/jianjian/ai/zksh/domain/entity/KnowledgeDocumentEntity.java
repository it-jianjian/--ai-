package com.jianjian.ai.zksh.domain.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class KnowledgeDocumentEntity {
    private Long id;
    private String title;
    private String source;
    private String tags;
    private String status;
    private Integer chunkCount;
    private Long createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

