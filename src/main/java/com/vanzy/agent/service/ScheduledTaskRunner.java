package com.vanzy.agent.service;

import com.vanzy.agent.config.AgentProperties;
import com.vanzy.agent.model.ChatRequest;
import com.vanzy.agent.model.ChatResponse;
import com.vanzy.agent.persistence.ScheduledTask;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * 定时任务调度器: 周期性轮询到期任务,通过 ChatService 同步对话执行,并回写结果。
 *
 * @author VanzyLiu
 */
@Slf4j
@Component
public class ScheduledTaskRunner {

    private final ScheduledTaskService taskService;
    private final ChatService chatService;
    private final AgentProperties properties;
    private final AuditLogService auditLogService;

    public ScheduledTaskRunner(ScheduledTaskService taskService, ChatService chatService,
                               AgentProperties properties, AuditLogService auditLogService) {
        this.taskService = taskService;
        this.chatService = chatService;
        this.properties = properties;
        this.auditLogService = auditLogService;
    }

    @Scheduled(fixedDelayString = "${agent.tools.scheduler.tick-ms:60000}")
    public void runDueTasks() {
        if (!properties.getTools().getScheduler().isEnabled()) {
            return;
        }
        List<ScheduledTask> due = taskService.dueTasks();
        if (due.isEmpty()) return;

        for (ScheduledTask task : due) {
            try {
                taskService.markRunning(task);
                String sessionId = "sched-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
                ChatRequest req = new ChatRequest();
                req.setSessionId(sessionId);
                req.setMessage(task.getMessage());
                req.setEnableTools(false);
                req.setEnableRag(false);
                ChatResponse resp = chatService.chat(req);
                taskService.markCompleted(task, resp.getContent(), sessionId);
                auditLogService.log(AuditLogService.ACTION_SCHEDULE, sessionId, null,
                        "定时任务执行成功: id=" + task.getId() + " name=" + task.getName(), true, resp.getDurationMs());
                log.info("定时任务执行成功: id={}, name={}", task.getId(), task.getName());
            } catch (Exception e) {
                taskService.markFailed(task, "执行失败: " + e.getMessage());
                auditLogService.log(AuditLogService.ACTION_SCHEDULE, null, null,
                        "定时任务执行失败: id=" + task.getId() + " err=" + e.getMessage(), false, null);
                log.error("定时任务执行失败: id={}", task.getId(), e);
            }
        }
    }
}
