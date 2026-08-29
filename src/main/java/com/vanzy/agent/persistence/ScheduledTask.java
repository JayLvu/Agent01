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
 * 定时任务实体: schedule_task 工具创建,由调度器周期性执行
 *
 * @author VanzyLiu
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "scheduled_task", indexes = {
        @Index(name = "idx_task_status_due", columnList = "status,dueAt")
})
public class ScheduledTask {

    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_RUNNING = "RUNNING";
    public static final String STATUS_COMPLETED = "COMPLETED";
    public static final String STATUS_FAILED = "FAILED";
    public static final String STATUS_CANCELLED = "CANCELLED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 任务名称 */
    @Column(length = 128)
    private String name;

    /** 要执行的消息/指令(交给 LLM) */
    @Column(length = 4000)
    private String message;

    /** 执行时间点 */
    @Column(nullable = false)
    private LocalDateTime dueAt;

    /** 状态 */
    @Column(nullable = false, length = 16)
    private String status;

    /** 执行结果(LLM 返回内容) */
    @Column(length = 8000)
    private String result;

    /** 执行时使用的会话 ID */
    @Column(length = 64)
    private String sessionId;

    private LocalDateTime executedAt;

    @Column(nullable = false)
    private LocalDateTime createdAt;
}
