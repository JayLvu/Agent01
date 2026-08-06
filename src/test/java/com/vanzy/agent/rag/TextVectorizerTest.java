package com.vanzy.agent.rag;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * TextVectorizer 单元测试
 *
 * @author VanzyLiu
 */
class TextVectorizerTest {

    private final TextVectorizer vectorizer = new TextVectorizer(256);

    @Test
    void vectorize_emptyText_returnsZeroVector() {
        float[] result = vectorizer.vectorize("");
        assertEquals(256, result.length);
        // 全零向量
        for (float v : result) {
            assertEquals(0f, v, 1e-9);
        }
    }

    @Test
    void vectorize_normalText_returnsNormalizedVector() {
        float[] v1 = vectorizer.vectorize("你好世界");
        assertEquals(256, v1.length);
        // 验证 L2 归一化: 模长应为 1
        double norm = 0;
        for (float v : v1) norm += v * v;
        assertEquals(1.0, Math.sqrt(norm), 1e-6);
    }

    @Test
    void cosineSimilarity_sameText_isOne() {
        float[] v = vectorizer.vectorize("Spring Boot AI Agent");
        double sim = TextVectorizer.cosineSimilarity(v, v);
        assertEquals(1.0, sim, 1e-6);
    }

    @Test
    void cosineSimilarity_similarText_higherThanUnrelated() {
        float[] q = vectorizer.vectorize("AI 对话 Agent");
        float[] similar = vectorizer.vectorize("AI 对话助手 Agent 系统");
        float[] unrelated = vectorizer.vectorize("今天天气真好");

        double sim1 = TextVectorizer.cosineSimilarity(q, similar);
        double sim2 = TextVectorizer.cosineSimilarity(q, unrelated);
        assertTrue(sim1 > sim2, "相似文本的余弦相似度应高于无关文本");
    }

    @Test
    void vectorStringConversion_roundTrip() {
        float[] original = vectorizer.vectorize("测试数据");
        String str = TextVectorizer.vectorToString(original);
        float[] parsed = TextVectorizer.stringToVector(str);
        assertArrayEquals(original, parsed, 1e-6f);
    }
}
