package com.vanzy.agent.rag;

import java.util.ArrayList;
import java.util.List;

/**
 * 简化的文本向量化工具
 *
 * 实现策略: 字符 n-gram + hash 映射到固定维度向量
 * 优点: 无需额外 embedding 模型,纯本地计算,中文友好
 * 缺点: 语义理解能力弱于真正 embedding(但足够 demo)
 *
 * @author VanzyLiu
 */
public class TextVectorizer {

    private final int vectorDim;

    public TextVectorizer(int vectorDim) {
        this.vectorDim = vectorDim;
    }

    /**
     * 将文本转为固定维度向量
     * 算法: 提取 2-gram 和 3-gram,hash 到 [0, dim) 区间累加
     */
    public float[] vectorize(String text) {
        if (text == null || text.isEmpty()) {
            return new float[vectorDim];
        }
        float[] vector = new float[vectorDim];
        String normalized = text.toLowerCase().replaceAll("\\s+", "");

        // 2-gram
        for (String ngram : extractNgrams(normalized, 2)) {
            int idx = Math.floorMod(ngram.hashCode(), vectorDim);
            vector[idx] += 1.0f;
        }
        // 3-gram
        for (String ngram : extractNgrams(normalized, 3)) {
            int idx = Math.floorMod(ngram.hashCode(), vectorDim);
            vector[idx] += 1.0f;
        }
        // L2 归一化
        return normalize(vector);
    }

    private List<String> extractNgrams(String text, int n) {
        List<String> ngrams = new ArrayList<>();
        if (text.length() < n) {
            ngrams.add(text);
            return ngrams;
        }
        for (int i = 0; i <= text.length() - n; i++) {
            ngrams.add(text.substring(i, i + n));
        }
        return ngrams;
    }

    private float[] normalize(float[] vector) {
        double norm = 0;
        for (float v : vector) {
            norm += v * v;
        }
        norm = Math.sqrt(norm);
        if (norm < 1e-9) {
            return vector;
        }
        for (int i = 0; i < vector.length; i++) {
            vector[i] = (float) (vector[i] / norm);
        }
        return vector;
    }

    /**
     * 计算两个向量的余弦相似度
     */
    public static double cosineSimilarity(float[] a, float[] b) {
        if (a.length != b.length) {
            throw new IllegalArgumentException("向量维度不一致");
        }
        double dot = 0, normA = 0, normB = 0;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }
        double denom = Math.sqrt(normA) * Math.sqrt(normB);
        return denom < 1e-9 ? 0 : dot / denom;
    }

    /**
     * 将向量转为字符串便于存储
     */
    public static String vectorToString(float[] vector) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < vector.length; i++) {
            if (i > 0) sb.append(",");
            sb.append(vector[i]);
        }
        return sb.toString();
    }

    /**
     * 从字符串解析向量
     */
    public static float[] stringToVector(String str) {
        if (str == null || str.isEmpty()) {
            return new float[0];
        }
        String[] parts = str.split(",");
        float[] vector = new float[parts.length];
        for (int i = 0; i < parts.length; i++) {
            vector[i] = Float.parseFloat(parts[i].trim());
        }
        return vector;
    }
}
