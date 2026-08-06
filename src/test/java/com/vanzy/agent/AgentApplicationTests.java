package com.vanzy.agent;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.redis.core.RedisTemplate;

import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * 应用上下文加载测试
 *
 * @author VanzyLiu
 */
@SpringBootTest
class AgentApplicationTests {

    @MockBean
    private RedisTemplate<String, Object> redisTemplate;

    @Test
    void contextLoads() {
        // 验证 Spring 上下文能正常加载
        AgentApplication app = new AgentApplication();
        assertNotNull(app);
    }
}
