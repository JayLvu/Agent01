package com.vanzy.agent.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Skill 元数据实体
 *
 * Skill 是一种用户自定义的 "知识/提示注入" 载体（通常为 markdown 文件）。
 * 每次对话时会自动读取所有已启用 Skill 的内容，追加到 System Prompt 中，
 * 让 LLM 具备特定领域的知识或遵循特定行为规则。
 *
 * @author VanzyLiu
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Skill {

    /** Skill ID(UUID 无短横) */
    private String id;

    /** 显示名(取自文件名或元数据) */
    private String name;

    /** 文件名 */
    private String fileName;

    /** 文件大小(字节) */
    private Long fileSize;

    /** 内容长度(字符) */
    private Integer contentLength;

    /** 是否启用(禁用则不注入) */
    @Builder.Default
    private Boolean enabled = true;

    /** 上传时间 */
    private LocalDateTime uploadedAt;
}
