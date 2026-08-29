package com.vanzy.agent.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 审计日志实体: 记录关键操作(对话、工具调用、取消、定时任务等)
 *
 * @author VanzyLiu
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "audit_log", indexes = {
        @Index(name = "idx_audit_session", columnList = "sessionId"),
        @Index(name = "idx_audit_action", columnList = "action"),
        @Index(name = "idx_audit_created", columnList = "createdAt")
})
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 操作类型: CHAT / TOOL_CALL / CANCEL / SCHEDULE / MODEL / SYSTEM */
    @Column(nullable = false, length = 32)
    private String action;

    /** 关联会话 ID */
    @Column(length = 64)
    private String sessionId;

    /** 操作者标识(多租户预留) */
    @Column(length = 64)
    private String actor;

    /** 工具名(工具类操作时) */
    @Column(length = 64)
    private String toolName;

    /** 操作详情(可截断) */
    @Column(length = 2000)
    private String detail;

    /** 是否成功 */
    private Boolean success;

    /** 耗时(ms) */
    private Long durationMs;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    public static AuditLog of(String action, String sessionId, String toolName, String detail,
                              Boolean success, Long durationMs) {
        return AuditLog.builder()
                .action(action)
                .sessionId(sessionId)
                .toolName(toolName)
                .detail(detail)
                .success(success)
                .durationMs(durationMs)
                .createdAt(LocalDateTime.now())
                .build();
    }
}
