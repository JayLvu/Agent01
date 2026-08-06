package com.vanzy.agent.rag;

import com.vanzy.agent.model.Document;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 文档管理服务: 解析、分块、存储用户上传的文档
 *
 * @author VanzyLiu
 */
public interface DocumentService {

    /**
     * 上传并解析文档
     *
     * @param file 上传的文件(pdf/docx/txt/md)
     * @return 文档元数据
     */
    Document uploadDocument(MultipartFile file);

    /**
     * 列出所有已上传文档
     *
     * @return 文档列表
     */
    List<Document> listDocuments();

    /**
     * 删除文档及其所有分块
     *
     * @param documentId 文档 ID
     */
    void deleteDocument(String documentId);

    /**
     * 获取文档所有分块
     *
     * @param documentId 文档 ID
     * @return 分块列表
     */
    List<String> getDocumentChunks(String documentId);
}
