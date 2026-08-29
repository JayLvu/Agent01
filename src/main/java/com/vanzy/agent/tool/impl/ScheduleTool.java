package com.vanzy.agent.tool.impl;

import com.vanzy.agent.config.AgentProperties;
import com.vanzy.agent.persistence.ScheduledTask;
import com.vanzy.agent.service.ScheduledTaskService;
import com.vanzy.agent.tool.Tool;
import com.vanzy.agent.tool.ToolResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 定时任务工具(schedule_task): 让 LLM 能创建一个延迟执行的提醒/任务。
 *
 * 创建后由 ScheduledTaskRunner 周期性轮询,到期后调用 LLM 执行该消息,
 * 结果可到"定时任务"页面查看。
 *
 * @author VanzyLiu
 */
@Slf4j
@Component
public class ScheduleTool implements Tool {

    private final ScheduledTaskService taskService;
    private final AgentProperties.SchedulerConfig config;

    public ScheduleTool(ScheduledTaskService taskService, AgentProperties agentProperties) {
        this.taskService = taskService;
        this.config = agentProperties.getTools().getScheduler();
    }

    @Override
    public String getName() {
        return "schedule_task";
    }

    @Override
    public String getDescription() {
        return "创建一个延迟执行的定时任务或提醒。指定要执行的内容和延迟秒数,到期后系统会执行并记录结果。";
    }

    @Override
    public Map<String, Object> getParametersSchema() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");

        Map<String, Object> name = new LinkedHashMap<>();
        name.put("type", "string");
        name.put("description", "任务名称,如 '每日提醒'");

        Map<String, Object> message = new LinkedHashMap<>();
        message.put("type", "string");
        message.put("description", "要执行的内容/提醒事项");

        Map<String, Object> delay = new LinkedHashMap<>();
        delay.put("type", "integer");
        delay.put("description", "延迟执行的秒数,如 300 表示 5 分钟后");

        Map<String, Object> props = new LinkedHashMap<>();
        props.put("name", name);
        props.put("message", message);
        props.put("delaySeconds", delay);
        schema.put("properties", props);
        schema.put("required", List.of("message", "delaySeconds"));
        return schema;
    }

    @Override
    public ToolResult execute(Map<String, Object> arguments) {
        if (!config.isEnabled()) {
            return ToolResult.error("定时任务工具已被禁用(agent.tools.scheduler.enabled=false)");
        }
        String name = arguments.get("name") == null ? "" : String.valueOf(arguments.get("name"));
        String message = arguments.get("message") == null ? "" : String.valueOf(arguments.get("message"));
        long delay = parseLong(arguments.get("delaySeconds"), 0);
        if (message.isBlank()) {
            return ToolResult.error("message 参数不能为空");
        }
        if (delay <= 0) {
            return ToolResult.error("delaySeconds 必须为正整数");
        }
        // 限制最大延迟 30 天,避免异常值
        delay = Math.min(delay, 30L * 24 * 3600);

        ScheduledTask task = taskService.create(name, message, delay);
        String due = task.getDueAt().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        return ToolResult.success(String.format("定时任务已创建: id=%d, 名称=%s, 执行时间=%s", task.getId(), task.getName(), due));
    }

    private long parseLong(Object o, long def) {
        if (o == null) return def;
        try {
            return Long.parseLong(String.valueOf(o));
        } catch (NumberFormatException e) {
            return def;
        }
    }
}
