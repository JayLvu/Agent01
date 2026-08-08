package com.vanzy.agent.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 对话附带的附件文件信息（前端先把小文件内容读出来再传给后端，避免 multipart/SSE 混用复杂性）
 *
 * @author VanzyLiu
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AttachmentFile {

    /** 文件名 */
    private String fileName;

    /** 文件 MIME / 类型(可选) */
    private String fileType;

    /** 文件大小(字节) */
    private Long fileSize;

    /** 文件的文本内容(已由前端或调用方读取) */
    private String content;
}
