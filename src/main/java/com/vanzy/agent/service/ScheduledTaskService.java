package com.vanzy.agent.service;

import com.vanzy.agent.persistence.ScheduledTask;
import com.vanzy.agent.persistence.ScheduledTaskRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 定时任务服务: 创建 / 取消 / 查询 / 认领待执行任务。
 *
 * @author VanzyLiu
 */
@Slf4j
@Service
public class ScheduledTaskService {

    private final ScheduledTaskRepository repository;

    public ScheduledTaskService(ScheduledTaskRepository repository) {
        this.repository = repository;
    }

    /** 创建一个延迟执行的任务 */
    public ScheduledTask create(String name, String message, long delaySeconds) {
        ScheduledTask task = ScheduledTask.builder()
                .name(name == null || name.isBlank() ? "定时任务" : name)
                .message(message)
                .dueAt(LocalDateTime.now().plusSeconds(delaySeconds))
                .status(ScheduledTask.STATUS_PENDING)
                .createdAt(LocalDateTime.now())
                .build();
        return repository.save(task);
    }

    public List<ScheduledTask> list() {
        return repository.findTop100ByOrderByIdDesc();
    }

    /** 取消任务 */
    public boolean cancel(Long id) {
        return repository.findById(id).map(t -> {
            if (ScheduledTask.STATUS_PENDING.equals(t.getStatus())) {
                t.setStatus(ScheduledTask.STATUS_CANCELLED);
                repository.save(t);
                return true;
            }
            return false;
        }).orElse(false);
    }

    /** 查询所有到期且待执行的任务 */
    public List<ScheduledTask> dueTasks() {
        return repository.findByStatusAndDueAtBeforeOrderByDueAtAsc(
                ScheduledTask.STATUS_PENDING, LocalDateTime.now());
    }

    /** 标记开始 / 结束 */
    public void markRunning(ScheduledTask task) {
        task.setStatus(ScheduledTask.STATUS_RUNNING);
        repository.save(task);
    }

    public void markCompleted(ScheduledTask task, String result, String sessionId) {
        task.setStatus(ScheduledTask.STATUS_COMPLETED);
        task.setResult(result);
        task.setSessionId(sessionId);
        task.setExecutedAt(LocalDateTime.now());
        repository.save(task);
    }

    public void markFailed(ScheduledTask task, String error) {
        task.setStatus(ScheduledTask.STATUS_FAILED);
        task.setResult(error);
        task.setExecutedAt(LocalDateTime.now());
        repository.save(task);
    }
}
