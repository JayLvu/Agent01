package com.vanzy.agent.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * 审计日志仓库
 *
 * @author VanzyLiu
 */
public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    List<AuditLog> findTop200ByOrderByIdDesc();

    List<AuditLog> findTop200ByActionOrderByIdDesc(String action);

    List<AuditLog> findTop200BySessionIdOrderByIdDesc(String sessionId);
}
