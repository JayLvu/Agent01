package com.vanzy.agent.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 文档元数据 - 用户上传的原始文档信息
 *
 * @author VanzyLiu
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Document {

    /** 文档 ID */
    private String id;

    /** 原始文件名 */
    private String fileName;

    /** 文件类型: pdf / docx / txt / md */
    private String fileType;

    /** 文件大小(字节) */
    private long fileSize;

    /** 文档总字符数 */
    private int totalChars;

    /** 分块数量 */
    private int chunkCount;

    /** 上传时间 */
    private LocalDateTime uploadedAt;
}
