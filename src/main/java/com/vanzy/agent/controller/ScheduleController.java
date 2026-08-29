package com.vanzy.agent.controller;

import com.vanzy.agent.persistence.ScheduledTask;
import com.vanzy.agent.service.ScheduledTaskService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 定时任务管理 API
 *
 * @author VanzyLiu
 */
@RestController
@RequestMapping("/schedules")
public class ScheduleController {

    private final ScheduledTaskService taskService;

    public ScheduleController(ScheduledTaskService taskService) {
        this.taskService = taskService;
    }

    @GetMapping
    public List<ScheduledTask> list() {
        return taskService.list();
    }

    /** 手动创建定时任务 */
    @PostMapping
    public ScheduledTask create(@RequestBody Map<String, Object> body) {
        String name = String.valueOf(body.getOrDefault("name", ""));
        String message = String.valueOf(body.getOrDefault("message", ""));
        long delaySeconds = Long.parseLong(String.valueOf(body.getOrDefault("delaySeconds", "300")));
        return taskService.create(name, message, delaySeconds);
    }

    @DeleteMapping("/{id}")
    public Map<String, Object> cancel(@PathVariable Long id) {
        boolean ok = taskService.cancel(id);
        return Map.of("ok", ok, "id", id);
    }
}
