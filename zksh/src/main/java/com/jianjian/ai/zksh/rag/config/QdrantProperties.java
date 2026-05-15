package com.jianjian.ai.zksh.rag.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Qdrant 连接配置（application.yml -> qdrant.*）。
 *
 * <p>用于将部署环境差异（地址、collection 名）从代码里剥离出来。</p>
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "qdrant")
public class QdrantProperties {
    private String url;
    private String collection;
}

