package com.vanzy.agent.rag;

import com.vanzy.agent.model.DocumentChunk;

import java.util.List;

/**
 * 向量存储接口: 文档块的向量化存储与检索
 *
 * @author VanzyLiu
 */
public interface VectorStore {

    /**
     * 存储文档块(含向量)
     *
     * @param chunk 文档块
     */
    void store(DocumentChunk chunk);

    /**
     * 批量存储
     *
     * @param chunks 文档块列表
     */
    void storeBatch(List<DocumentChunk> chunks);

    /**
     * 根据查询向量检索最相似的 top-k 文档块
     *
     * @param queryVector 查询向量
     * @param topK        返回数量
     * @return 相似文档块列表(按相似度降序)
     */
    List<DocumentChunk> search(float[] queryVector, int topK);

    /**
     * 删除文档的所有分块
     *
     * @param documentId 文档 ID
     */
    void deleteByDocumentId(String documentId);

    /**
     * 删除所有文档块
     */
    void deleteAll();
}
