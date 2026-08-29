package com.vanzy.agent.service;

import com.vanzy.agent.persistence.AuditLog;
import com.vanzy.agent.persistence.AuditLogRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 审计日志服务: 记录关键操作并支持查询。
 *
 * @author VanzyLiu
 */
@Slf4j
@Service
public class AuditLogService {

    public static final String ACTION_CHAT = "CHAT";
    public static final String ACTION_TOOL_CALL = "TOOL_CALL";
    public static final String ACTION_CANCEL = "CANCEL";
    public static final String ACTION_SCHEDULE = "SCHEDULE";
    public static final String ACTION_MODEL = "MODEL";

    private final AuditLogRepository repository;

    public AuditLogService(AuditLogRepository repository) {
        this.repository = repository;
    }

    public void log(String action, String sessionId, String toolName, String detail,
                    Boolean success, Long durationMs) {
        try {
            String safeDetail = StringUtils.hasText(detail) && detail.length() > 2000
                    ? detail.substring(0, 2000) : detail;
            repository.save(AuditLog.of(action, sessionId, toolName, safeDetail, success, durationMs));
        } catch (Exception e) {
            // 审计失败不应影响主流程
            log.warn("审计日志写入失败: action={}, err={}", action, e.getMessage());
        }
    }

    public List<AuditLog> recent() {
        return repository.findTop200ByOrderByIdDesc();
    }

    public List<AuditLog> recentByAction(String action) {
        return repository.findTop200ByActionOrderByIdDesc(action);
    }

    public List<AuditLog> recentBySession(String sessionId) {
        return repository.findTop200BySessionIdOrderByIdDesc(sessionId);
    }
}
