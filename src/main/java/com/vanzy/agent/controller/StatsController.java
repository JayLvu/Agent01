package com.vanzy.agent.controller;

import com.vanzy.agent.persistence.TokenUsageRecord;
import com.vanzy.agent.service.TokenUsageService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Token/成本统计 API
 *
 * @author VanzyLiu
 */
@RestController
@RequestMapping("/stats")
public class StatsController {

    private final TokenUsageService tokenUsageService;

    public StatsController(TokenUsageService tokenUsageService) {
        this.tokenUsageService = tokenUsageService;
    }

    /** 汇总统计 */
    @GetMapping("/summary")
    public Map<String, Object> summary() {
        return tokenUsageService.summary();
    }

    /** 最近使用明细 */
    @GetMapping("/usage")
    public List<TokenUsageRecord> usage() {
        return tokenUsageService.recent();
    }

    /** 汇总 + 明细一次性返回(便于前端一次请求) */
    @GetMapping("/overview")
    public Map<String, Object> overview() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("summary", tokenUsageService.summary());
        m.put("recent", tokenUsageService.recent());
        return m;
    }
}
