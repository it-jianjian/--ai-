package com.jianjian.ai.zksh.mapper;

import com.jianjian.ai.zksh.domain.entity.KnowledgeDocumentEntity;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface KnowledgeDocumentMapper {
    int insert(KnowledgeDocumentEntity entity);

    KnowledgeDocumentEntity selectById(@Param("id") Long id);

    List<KnowledgeDocumentEntity> selectByStatus(@Param("status") String status);
    
    List<KnowledgeDocumentEntity> selectByCreatedBy(@Param("createdBy") Long createdBy);

    int updateStatus(@Param("id") Long id, @Param("status") String status, @Param("chunkCount") Integer chunkCount);
}

