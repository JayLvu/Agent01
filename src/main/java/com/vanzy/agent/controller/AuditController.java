package com.vanzy.agent.controller;

import com.vanzy.agent.persistence.AuditLog;
import com.vanzy.agent.service.AuditLogService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 审计日志 API
 *
 * @author VanzyLiu
 */
@RestController
@RequestMapping("/audit")
public class AuditController {

    private final AuditLogService auditLogService;

    public AuditController(AuditLogService auditLogService) {
        this.auditLogService = auditLogService;
    }

    @GetMapping("/logs")
    public List<AuditLog> logs(@RequestParam(required = false) String action,
                               @RequestParam(required = false) String sessionId) {
        if (sessionId != null && !sessionId.isBlank()) {
            return auditLogService.recentBySession(sessionId);
        }
        if (action != null && !action.isBlank()) {
            return auditLogService.recentByAction(action);
        }
        return auditLogService.recent();
    }
}
