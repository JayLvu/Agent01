package com.vanzy.agent.rag.impl;

import com.vanzy.agent.config.AgentProperties;
import com.vanzy.agent.exception.AgentException;
import com.vanzy.agent.model.Document;
import com.vanzy.agent.model.DocumentChunk;
import com.vanzy.agent.rag.DocumentService;
import com.vanzy.agent.rag.TextVectorizer;
import com.vanzy.agent.rag.VectorStore;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * 文档管理服务实现
 *
 * 支持文件类型: PDF / DOCX / TXT / MD
 * 流程: 保存文件 → 解析文本 → 分块 → 向量化 → 入库
 *
 * @author VanzyLiu
 */
@Slf4j
@Service
public class DocumentServiceImpl implements DocumentService {

    private static final String DOC_META_PREFIX = "agent:doc:meta:";
    private static final String DOC_INDEX_KEY = "agent:doc:index";

    private final VectorStore vectorStore;
    private final TextVectorizer vectorizer;
    private final AgentProperties properties;
    private final RedisTemplate<String, Object> redisTemplate;

    public DocumentServiceImpl(VectorStore vectorStore, AgentProperties properties,
                               RedisTemplate<String, Object> redisTemplate) {
        this.vectorStore = vectorStore;
        this.vectorizer = new TextVectorizer(properties.getRag().getVectorDim());
        this.properties = properties;
        this.redisTemplate = redisTemplate;
    }

    @Override
    public Document uploadDocument(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new AgentException("上传文件不能为空");
        }
        String fileName = file.getOriginalFilename();
        String fileType = detectFileType(fileName);

        try {
            // 1. 保存原始文件到磁盘
            Path uploadDir = Paths.get(properties.getRag().getUploadDir());
            Files.createDirectories(uploadDir);
            String docId = UUID.randomUUID().toString().replace("-", "");
            Path saved = uploadDir.resolve(docId + "." + fileType);
            Files.copy(file.getInputStream(), saved, StandardCopyOption.REPLACE_EXISTING);

            // 2. 解析文本
            String text = parseText(saved, fileType, file.getBytes());
            if (!StringUtils.hasText(text)) {
                throw new AgentException("文档解析后文本为空");
            }

            // 3. 分块 + 向量化 + 入库
            List<DocumentChunk> chunks = splitAndVectorize(docId, fileName, text);
            vectorStore.storeBatch(chunks);

            // 4. 保存元数据
            Document doc = Document.builder()
                    .id(docId)
                    .fileName(fileName)
                    .fileType(fileType)
                    .fileSize(file.getSize())
                    .totalChars(text.length())
                    .chunkCount(chunks.size())
                    .uploadedAt(LocalDateTime.now())
                    .build();
            redisTemplate.opsForHash().put(DOC_META_PREFIX + docId, "meta", doc);
            redisTemplate.opsForSet().add(DOC_INDEX_KEY, docId);

            log.info("文档上传成功: {}, 分块数: {}", fileName, chunks.size());
            return doc;

        } catch (IOException e) {
            throw new AgentException("文档处理失败: " + e.getMessage(), e);
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<Document> listDocuments() {
        Set<Object> ids = redisTemplate.opsForSet().members(DOC_INDEX_KEY);
        if (ids == null) return List.of();
        List<Document> docs = new ArrayList<>();
        for (Object id : ids) {
            Object meta = redisTemplate.opsForHash().get(DOC_META_PREFIX + id, "meta");
            if (meta instanceof Document d) {
                docs.add(d);
            }
        }
        return docs;
    }

    @Override
    public void deleteDocument(String documentId) {
        vectorStore.deleteByDocumentId(documentId);
        redisTemplate.delete(DOC_META_PREFIX + documentId);
        redisTemplate.opsForSet().remove(DOC_INDEX_KEY, documentId);
        log.info("文档已删除: {}", documentId);
    }

    @Override
    public List<String> getDocumentChunks(String documentId) {
        // 简化实现: 返回分块内容列表(从 vectorStore 读取)
        // 实际项目可单独维护 chunk 索引
        return List.of();
    }

    private String detectFileType(String fileName) {
        if (fileName == null) throw new AgentException("文件名不能为空");
        String lower = fileName.toLowerCase();
        if (lower.endsWith(".pdf")) return "pdf";
        if (lower.endsWith(".docx")) return "docx";
        if (lower.endsWith(".txt")) return "txt";
        if (lower.endsWith(".md")) return "md";
        throw new AgentException("不支持的文件类型,仅支持: pdf/docx/txt/md");
    }

    private String parseText(Path file, String fileType, byte[] bytes) throws IOException {
        return switch (fileType) {
            case "pdf" -> parsePdf(bytes);
            case "docx" -> parseDocx(bytes);
            case "txt", "md" -> Files.readString(file);
            default -> throw new AgentException("不支持的文件类型: " + fileType);
        };
    }

    private String parsePdf(byte[] bytes) throws IOException {
        try (PDDocument doc = Loader.loadPDF(bytes)) {
            PDFTextStripper stripper = new PDFTextStripper();
            return stripper.getText(doc);
        }
    }

    private String parseDocx(byte[] bytes) throws IOException {
        try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(bytes))) {
            StringBuilder sb = new StringBuilder();
            for (XWPFParagraph p : doc.getParagraphs()) {
                sb.append(p.getText()).append("\n");
            }
            return sb.toString();
        }
    }

    private List<DocumentChunk> splitAndVectorize(String docId, String docName, String text) {
        int chunkSize = properties.getRag().getChunkSize();
        int overlap = properties.getRag().getChunkOverlap();
        List<DocumentChunk> chunks = new ArrayList<>();

        int start = 0;
        int index = 0;
        while (start < text.length()) {
            int end = Math.min(start + chunkSize, text.length());
            String content = text.substring(start, end);
            float[] vector = vectorizer.vectorize(content);
            chunks.add(DocumentChunk.builder()
                    .id(UUID.randomUUID().toString().replace("-", ""))
                    .documentId(docId)
                    .documentName(docName)
                    .chunkIndex(index++)
                    .content(content)
                    .vector(vector)
                    .build());
            if (end >= text.length()) break;
            start = end - overlap;
            if (start < 0) start = 0;
        }
        return chunks;
    }
}
