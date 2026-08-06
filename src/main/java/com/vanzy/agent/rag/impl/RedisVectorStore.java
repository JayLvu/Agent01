package com.vanzy.agent.rag.impl;

import com.vanzy.agent.exception.AgentException;
import com.vanzy.agent.model.DocumentChunk;
import com.vanzy.agent.rag.TextVectorizer;
import com.vanzy.agent.rag.VectorStore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

/**
 * 基于 Redis 的向量存储实现
 *
 * 存储结构:
 * - agent:vector:chunk:{chunkId} -> DocumentChunk(含向量字符串)的 hash
 * - agent:vector:index           -> 所有 chunkId 的 set(用于遍历检索)
 *
 * 检索方式: 暴力扫描 + 余弦相似度(适合小规模 demo,生产建议用 RediSearch)
 *
 * @author VanzyLiu
 */
@Slf4j
@Component
@Lazy
public class RedisVectorStore implements VectorStore {

    private static final String CHUNK_KEY_PREFIX = "agent:vector:chunk:";
    private static final String INDEX_KEY = "agent:vector:index";

    private final RedisTemplate<String, Object> redisTemplate;

    @Autowired
    public RedisVectorStore(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public void store(DocumentChunk chunk) {
        try {
            String key = CHUNK_KEY_PREFIX + chunk.getId();
            redisTemplate.opsForHash().put(key, "documentId", chunk.getDocumentId());
            redisTemplate.opsForHash().put(key, "documentName", chunk.getDocumentName());
            redisTemplate.opsForHash().put(key, "chunkIndex", chunk.getChunkIndex());
            redisTemplate.opsForHash().put(key, "content", chunk.getContent());
            redisTemplate.opsForHash().put(key, "vector", TextVectorizer.vectorToString(chunk.getVector()));
            redisTemplate.opsForSet().add(INDEX_KEY, chunk.getId());
            log.debug("存储文档块: id={}, doc={}", chunk.getId(), chunk.getDocumentName());
        } catch (DataAccessException e) {
            throw new AgentException("向量存储失败: " + e.getMessage(), e);
        }
    }

    @Override
    public void storeBatch(List<DocumentChunk> chunks) {
        chunks.forEach(this::store);
    }

    @Override
    public List<DocumentChunk> search(float[] queryVector, int topK) {
        Set<Object> chunkIds = redisTemplate.opsForSet().members(INDEX_KEY);
        if (chunkIds == null || chunkIds.isEmpty()) {
            return new ArrayList<>();
        }

        List<DocumentChunk> results = new ArrayList<>();
        for (Object idObj : chunkIds) {
            String chunkId = (String) idObj;
            String key = CHUNK_KEY_PREFIX + chunkId;
            DocumentChunk chunk = readChunk(chunkId, key);
            if (chunk != null) {
                double score = TextVectorizer.cosineSimilarity(queryVector, chunk.getVector());
                chunk.setScore(score);
                results.add(chunk);
            }
        }

        results.sort(Comparator.comparingDouble(DocumentChunk::getScore).reversed());
        if (results.size() > topK) {
            return results.subList(0, topK);
        }
        return results;
    }

    @Override
    public void deleteByDocumentId(String documentId) {
        Set<Object> chunkIds = redisTemplate.opsForSet().members(INDEX_KEY);
        if (chunkIds == null) return;
        for (Object idObj : chunkIds) {
            String chunkId = (String) idObj;
            String key = CHUNK_KEY_PREFIX + chunkId;
            Object docId = redisTemplate.opsForHash().get(key, "documentId");
            if (documentId.equals(docId)) {
                redisTemplate.delete(key);
                redisTemplate.opsForSet().remove(INDEX_KEY, chunkId);
            }
        }
        log.info("已删除文档 {} 的所有分块", documentId);
    }

    @Override
    public void deleteAll() {
        Set<Object> chunkIds = redisTemplate.opsForSet().members(INDEX_KEY);
        if (chunkIds == null) return;
        for (Object idObj : chunkIds) {
            redisTemplate.delete(CHUNK_KEY_PREFIX + idObj);
        }
        redisTemplate.delete(INDEX_KEY);
        log.info("已清空所有向量数据");
    }

    @SuppressWarnings("unchecked")
    private DocumentChunk readChunk(String chunkId, String key) {
        try {
            var entries = redisTemplate.opsForHash().entries(key);
            if (entries.isEmpty()) return null;
            String content = (String) entries.get("content");
            String vectorStr = (String) entries.get("vector");
            String documentId = (String) entries.get("documentId");
            String documentName = (String) entries.get("documentName");
            int chunkIndex = Integer.parseInt(String.valueOf(entries.get("chunkIndex")));
            return DocumentChunk.builder()
                    .id(chunkId)
                    .documentId(documentId)
                    .documentName(documentName)
                    .chunkIndex(chunkIndex)
                    .content(content)
                    .vector(TextVectorizer.stringToVector(vectorStr))
                    .build();
        } catch (Exception e) {
            log.warn("读取文档块失败: {}", chunkId, e);
            return null;
        }
    }
}
