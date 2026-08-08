package com.vanzy.agent.skill.impl;

import com.vanzy.agent.config.AgentProperties;
import com.vanzy.agent.exception.AgentException;
import com.vanzy.agent.model.Skill;
import com.vanzy.agent.skill.SkillService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Skill 服务实现
 *
 * - 元数据存 Redis
 * - 内容存磁盘（agent.skill.dir）
 * - 读取时自动合并所有启用 Skill 的内容, 以分隔符拼接用于 System Prompt 注入
 *
 * @author VanzyLiu
 */
@Slf4j
@Service
public class SkillServiceImpl implements SkillService {

    private static final String SKILL_META_PREFIX = "agent:skill:meta:";
    private static final String SKILL_INDEX_KEY = "agent:skill:index";
    private static final List<String> ALLOWED_EXT = List.of("md", "markdown", "txt");

    private final AgentProperties properties;
    private final RedisTemplate<String, Object> redisTemplate;

    public SkillServiceImpl(AgentProperties properties, RedisTemplate<String, Object> redisTemplate) {
        this.properties = properties;
        this.redisTemplate = redisTemplate;
    }

    @Override
    public Skill uploadSkill(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new AgentException("Skill 文件不能为空");
        }
        String fileName = file.getOriginalFilename();
        String ext = detectExt(fileName);
        if (!ALLOWED_EXT.contains(ext.toLowerCase())) {
            throw new AgentException("Skill 仅支持 .md / .markdown / .txt 格式");
        }

        try {
            Path skillDir = Paths.get(properties.getSkill().getDir());
            Files.createDirectories(skillDir);

            String id = UUID.randomUUID().toString().replace("-", "");
            Path saved = skillDir.resolve(id + "." + ext);
            Files.copy(file.getInputStream(), saved, StandardCopyOption.REPLACE_EXISTING);

            String content = Files.readString(saved, StandardCharsets.UTF_8);
            int contentLen = content == null ? 0 : content.length();
            long fileSize = file.getSize();

            String displayName = StringUtils.stripFilenameExtension(fileName);

            Skill skill = Skill.builder()
                    .id(id)
                    .name(displayName)
                    .fileName(fileName)
                    .fileSize(fileSize)
                    .contentLength(contentLen)
                    .enabled(true)
                    .uploadedAt(LocalDateTime.now())
                    .build();

            redisTemplate.opsForHash().put(SKILL_META_PREFIX + id, "meta", skill);
            redisTemplate.opsForSet().add(SKILL_INDEX_KEY, id);

            log.info("Skill 上传成功: name={}, size={}, contentLen={}", displayName, fileSize, contentLen);
            return skill;
        } catch (IOException e) {
            throw new AgentException("Skill 保存失败: " + e.getMessage(), e);
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<Skill> listSkills() {
        Set<Object> ids = redisTemplate.opsForSet().members(SKILL_INDEX_KEY);
        if (ids == null) return List.of();
        List<Skill> skills = new ArrayList<>();
        for (Object id : ids) {
            Object meta = redisTemplate.opsForHash().get(SKILL_META_PREFIX + id, "meta");
            if (meta instanceof Skill s) skills.add(s);
        }
        skills.sort(Comparator.comparing(Skill::getUploadedAt, Comparator.nullsLast(Comparator.reverseOrder())));
        return skills;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Skill toggleEnabled(String skillId, boolean enabled) {
        Object meta = redisTemplate.opsForHash().get(SKILL_META_PREFIX + skillId, "meta");
        if (!(meta instanceof Skill skill)) {
            throw new AgentException("Skill 不存在: " + skillId);
        }
        skill.setEnabled(enabled);
        redisTemplate.opsForHash().put(SKILL_META_PREFIX + skillId, "meta", skill);
        log.info("Skill {} enabled 状态更新为: {}", skillId, enabled);
        return skill;
    }

    @Override
    public void deleteSkill(String skillId) {
        Path skillDir = Paths.get(properties.getSkill().getDir());
        try (var stream = Files.list(skillDir)) {
            stream.filter(p -> p.getFileName().toString().startsWith(skillId + "."))
                    .forEach(p -> {
                        try { Files.deleteIfExists(p); } catch (IOException ignored) {}
                    });
        } catch (IOException ignored) {}

        redisTemplate.delete(SKILL_META_PREFIX + skillId);
        redisTemplate.opsForSet().remove(SKILL_INDEX_KEY, skillId);
        log.info("Skill 已删除: {}", skillId);
    }

    @Override
    public String getSkillContent(String skillId) {
        Path skillDir = Paths.get(properties.getSkill().getDir());
        try (var stream = Files.list(skillDir)) {
            Path file = stream.filter(p -> p.getFileName().toString().startsWith(skillId + "."))
                    .findFirst()
                    .orElse(null);
            if (file == null) return "";
            return Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            return "";
        }
    }

    @Override
    public String buildEnabledSkillsPrompt() {
        List<Skill> enabled = listSkills().stream()
                .filter(s -> Boolean.TRUE.equals(s.getEnabled()))
                .toList();
        if (enabled.isEmpty()) return "";

        StringBuilder sb = new StringBuilder();
        sb.append("\n\n---\n# 用户自定义 Skill（请你遵循以下知识与规则回答问题）\n");
        for (Skill s : enabled) {
            String content = getSkillContent(s.getId());
            if (!StringUtils.hasText(content)) continue;
            sb.append("\n## ").append(s.getName()).append("\n");
            sb.append(content).append("\n");
        }
        return sb.toString();
    }

    private String detectExt(String fileName) {
        if (fileName == null) throw new AgentException("文件名不能为空");
        int idx = fileName.lastIndexOf('.');
        if (idx < 0 || idx == fileName.length() - 1) {
            throw new AgentException("文件缺少扩展名");
        }
        return fileName.substring(idx + 1);
    }
}
