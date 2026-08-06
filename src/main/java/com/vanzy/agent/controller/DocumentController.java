package com.vanzy.agent.controller;

import com.vanzy.agent.model.Document;
import com.vanzy.agent.rag.DocumentService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 文档管理 HTTP API(RAG 模块)
 *
 * 端点:
 * - POST   /documents        上传文档(pdf/docx/txt/md)
 * - GET    /documents        列出所有文档
 * - DELETE /documents/{id}   删除文档及其分块
 *
 * @author VanzyLiu
 */
@Slf4j
@RestController
@RequestMapping("/documents")
public class DocumentController {

    private final DocumentService documentService;

    public DocumentController(DocumentService documentService) {
        this.documentService = documentService;
    }

    /**
     * 上传文档并自动分块、向量化、入库
     *
     * multipart/form-data,字段名: file
     */
    @PostMapping
    public Document upload(@RequestParam("file") MultipartFile file) {
        log.info("收到文档上传: fileName={}, size={}",
                file.getOriginalFilename(), file.getSize());
        return documentService.uploadDocument(file);
    }

    /**
     * 列出所有已上传文档
     */
    @GetMapping
    public List<Document> list() {
        return documentService.listDocuments();
    }

    /**
     * 删除文档及其所有分块
     */
    @DeleteMapping("/{documentId}")
    public String delete(@PathVariable String documentId) {
        documentService.deleteDocument(documentId);
        return "文档已删除: " + documentId;
    }
}
