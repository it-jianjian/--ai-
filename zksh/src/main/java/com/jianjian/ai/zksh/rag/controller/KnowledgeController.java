package com.jianjian.ai.zksh.rag.controller;

import com.jianjian.ai.zksh.common.ApiResponse;
import com.jianjian.ai.zksh.common.BizException;
import com.jianjian.ai.zksh.domain.entity.KnowledgeDocumentEntity;
import com.jianjian.ai.zksh.mapper.KnowledgeDocumentMapper;
import com.jianjian.ai.zksh.rag.service.KnowledgeIngestService;
import com.jianjian.ai.zksh.rag.service.RagReindexService;
import com.jianjian.ai.zksh.security.UserContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * RAG 知识库管理入口（V1）。
 *
 * <p>当前“文档上传 -> 导入索引”：
 * 支持 txt/md/pdf，导入后写入 MySQL 元数据与分片，并将向量 upsert 到 Qdrant。</p>
 */
@RestController
@RequestMapping("/api/rag")
public class KnowledgeController {

    @Autowired
    private KnowledgeIngestService ingestService;
    @Autowired
    private KnowledgeDocumentMapper knowledgeDocumentMapper;
    @Autowired
    private RagReindexService ragReindexService;

    @PostMapping("/knowledge/documents/upload")
    public ApiResponse<KnowledgeDocumentEntity> upload(@RequestParam("file") MultipartFile file,
                                                       @RequestParam(value = "title", required = false) String title,
                                                       @RequestParam(value = "tags", required = false) String tags) {
        Long userId = UserContext.getUserId();
        if (userId == null) {
            throw new BizException("请先登录后再操作");
        }
        return ApiResponse.ok(ingestService.ingest(userId, file, title, tags));
    }

    @GetMapping("/knowledge/documents")
    public ApiResponse<List<KnowledgeDocumentEntity>> listMyDocuments() {
        Long userId = UserContext.getUserId();
        if (userId == null) {
            throw new BizException("请先登录后再操作");
        }
        return ApiResponse.ok(knowledgeDocumentMapper.selectByCreatedBy(userId));
    }

    @PostMapping("/knowledge/reindex")
    public ApiResponse<RagReindexService.ReindexResult> reindexMyKnowledge() {
        Long userId = UserContext.getUserId();
        if (userId == null) {
            throw new BizException("请先登录后再操作");
        }
        return ApiResponse.ok(ragReindexService.reindexMyKnowledge(userId));
    }
}

