package com.jianjian.ai.zksh.mapper;

import com.jianjian.ai.zksh.domain.entity.KnowledgeChunkEntity;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface KnowledgeChunkMapper {
    int insert(KnowledgeChunkEntity entity);

    List<KnowledgeChunkEntity> selectByDocId(@Param("docId") Long docId);

    List<KnowledgeChunkEntity> selectByIds(@Param("ids") List<Long> ids);

    List<KnowledgeChunkEntity> selectByQdrantPointIds(@Param("qdrantPointIds") List<String> qdrantPointIds);

    List<KnowledgeChunkEntity> searchByKeyword(@Param("keyword") String keyword, @Param("limit") Integer limit);

    int updateQdrantPointId(@Param("id") Long id, @Param("qdrantPointId") String qdrantPointId);
}

