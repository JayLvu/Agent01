package com.vanzy.agent.rag.impl;

import com.vanzy.agent.config.AgentProperties;
import com.vanzy.agent.model.DocumentChunk;
import com.vanzy.agent.rag.RagService;
import com.vanzy.agent.rag.TextVectorizer;
import com.vanzy.agent.rag.VectorStore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.stream.Collectors;

/**
 * RAG 检索服务实现
 *
 * 流程:
 * 1. 用户查询 → 向量化
 * 2. 向量库检索 top-k 相似文档块
 * 3. 拼装为上下文 prompt(供 LLM 引用)
 *
 * @author VanzyLiu
 */
@Slf4j
@Service
public class RagServiceImpl implements RagService {

    private final VectorStore vectorStore;
    private final TextVectorizer vectorizer;
    private final AgentProperties properties;

    public RagServiceImpl(VectorStore vectorStore, AgentProperties properties) {
        this.vectorStore = vectorStore;
        this.vectorizer = new TextVectorizer(properties.getRag().getVectorDim());
        this.properties = properties;
    }

    @Override
    public List<DocumentChunk> retrieve(String query, int topK) {
        if (!StringUtils.hasText(query)) {
            return List.of();
        }
        float[] queryVector = vectorizer.vectorize(query);
        List<DocumentChunk> results = vectorStore.search(queryVector, topK);
        // 过滤低相似度结果
        return results.stream()
                .filter(c -> c.getScore() > 0.05)
                .collect(Collectors.toList());
    }

    @Override
    public String retrieveContext(String query) {
        if (!properties.getRag().isEnabled()) {
            return "";
        }
        List<DocumentChunk> chunks = retrieve(query, properties.getRag().getTopK());
        if (CollectionUtils.isEmpty(chunks)) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("以下是从知识库检索到的相关资料,请在回答时参考:\n\n");
        for (int i = 0; i < chunks.size(); i++) {
            DocumentChunk chunk = chunks.get(i);
            sb.append("【资料 ").append(i + 1).append("】(来源: ")
                    .append(chunk.getDocumentName()).append(")\n");
            sb.append(chunk.getContent()).append("\n\n");
        }
        sb.append("请基于以上资料回答用户问题。如果资料不足,请说明。\n");
        log.debug("RAG 检索到 {} 个相关文档块", chunks.size());
        return sb.toString();
    }
}
