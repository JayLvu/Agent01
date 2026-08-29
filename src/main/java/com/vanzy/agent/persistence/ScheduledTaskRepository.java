package com.vanzy.agent.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 定时任务仓库
 *
 * @author VanzyLiu
 */
public interface ScheduledTaskRepository extends JpaRepository<ScheduledTask, Long> {

    List<ScheduledTask> findByStatusAndDueAtBeforeOrderByDueAtAsc(String status, LocalDateTime dueAt);

    List<ScheduledTask> findTop100ByOrderByIdDesc();
}
