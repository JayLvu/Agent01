package com.vanzy.agent.controller;

import com.vanzy.agent.model.Skill;
import com.vanzy.agent.skill.SkillService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

/**
 * Skill 管理 HTTP API
 *
 * 端点:
 * - POST   /skills          上传 Skill(.md / .markdown / .txt)
 * - GET    /skills          列出所有 Skill
 * - GET    /skills/{id}/content  读取 Skill 内容
 * - PATCH  /skills/{id}/enabled 切换启用状态 ?enabled=true|false
 * - DELETE /skills/{id}     删除 Skill
 *
 * @author VanzyLiu
 */
@Slf4j
@RestController
@RequestMapping("/skills")
public class SkillController {

    private final SkillService skillService;

    public SkillController(SkillService skillService) {
        this.skillService = skillService;
    }

    @PostMapping
    public Skill upload(@RequestParam("file") MultipartFile file) {
        log.info("收到 Skill 上传: fileName={}, size={}",
                file.getOriginalFilename(), file.getSize());
        return skillService.uploadSkill(file);
    }

    @GetMapping
    public List<Skill> list() {
        return skillService.listSkills();
    }

    @GetMapping("/{id}/content")
    public Map<String, String> content(@PathVariable String id) {
        return Map.of("content", skillService.getSkillContent(id));
    }

    @PatchMapping("/{id}/enabled")
    public Skill toggleEnabled(@PathVariable String id,
                               @RequestParam boolean enabled) {
        return skillService.toggleEnabled(id, enabled);
    }

    @DeleteMapping("/{id}")
    public String delete(@PathVariable String id) {
        skillService.deleteSkill(id);
        return "Skill 已删除: " + id;
    }
}
