package com.jianjian.ai.zksh.rag.service;

import com.jianjian.ai.zksh.domain.vo.AiReferenceVO;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 按会话 memoryId 暂存本轮检索引用，供控制器在最终响应中回传 references。
 */
@Component
public class RagReferenceStore {

    private final Map<String, List<AiReferenceVO>> store = new ConcurrentHashMap<>();

    public void save(String memoryId, List<AiReferenceVO> references) {
        if (memoryId == null || memoryId.isBlank()) {
            return;
        }
        store.put(memoryId, references == null ? List.of() : references);
    }

    public List<AiReferenceVO> consume(String memoryId) {
        if (memoryId == null || memoryId.isBlank()) {
            return List.of();
        }
        List<AiReferenceVO> refs = store.remove(memoryId);
        return refs == null ? List.of() : refs;
    }
}

