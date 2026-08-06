package com.vanzy.agent.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 文档块 - RAG 检索的最小单元
 *
 * @author VanzyLiu
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DocumentChunk {

    /** 块 ID */
    private String id;

    /** 文档 ID */
    private String documentId;

    /** 文档名称 */
    private String documentName;

    /** 块索引(在原文中的位置) */
    private int chunkIndex;

    /** 文本内容 */
    private String content;

    /** 向量(简化实现: n-gram hash 向量) */
    private float[] vector;

    /** 相似度得分(RAG 检索时填充) */
    private double score;
}
