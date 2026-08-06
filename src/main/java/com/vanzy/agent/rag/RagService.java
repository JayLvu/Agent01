package com.vanzy.agent.rag;

import com.vanzy.agent.model.DocumentChunk;

import java.util.List;

/**
 * RAG 检索服务: 根据用户查询检索相关文档片段
 *
 * @author VanzyLiu
 */
public interface RagService {

    /**
     * 检索与查询相关的 top-k 文档块
     *
     * @param query 用户查询
     * @param topK  返回数量
     * @return 相关文档块列表(按相似度降序)
     */
    List<DocumentChunk> retrieve(String query, int topK);

    /**
     * 检索并拼装为上下文文本
     *
     * @param query 用户查询
     * @return 格式化的上下文文本(供 LLM 使用),空则返回空字符串
     */
    String retrieveContext(String query);
}
