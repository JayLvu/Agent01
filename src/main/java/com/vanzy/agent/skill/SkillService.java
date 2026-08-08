package com.vanzy.agent.skill;

import com.vanzy.agent.model.Skill;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * Skill 服务接口: 上传/列出/启用禁用/删除/读取内容
 *
 * @author VanzyLiu
 */
public interface SkillService {

    /**
     * 上传 Skill 文件(仅支持 .md / .markdown / .txt)
     */
    Skill uploadSkill(MultipartFile file);

    /**
     * 列出所有 Skill
     */
    List<Skill> listSkills();

    /**
     * 切换 Skill 启用/禁用状态
     */
    Skill toggleEnabled(String skillId, boolean enabled);

    /**
     * 删除指定 Skill(同时删除磁盘文件)
     */
    void deleteSkill(String skillId);

    /**
     * 读取某个 Skill 的内容
     */
    String getSkillContent(String skillId);

    /**
     * 读取所有已启用 Skill 的内容(按上传时间排序,用分隔符拼接)
     * 用于注入 System Prompt
     */
    String buildEnabledSkillsPrompt();
}
