package com.vanzy.agent.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * DeepSeek API 请求/响应 DTO(OpenAI 兼容格式)
 *
 * @author VanzyLiu
 */
public class DeepSeekDtos {

    /** DeepSeek 请求体 */
    @Data
    @NoArgsConstructor
    public static class DeepSeekRequest {
        private String model;
        private List<ChatMessage> messages;
        private Double temperature;
        @JsonProperty("max_tokens")
        private Integer maxTokens;
        private Boolean stream;
        /** 工具列表(OpenAI function calling 格式) */
        private List<Map<String, Object>> tools;
        /** 工具选择策略: "auto" / "none" / {"type":"function","function":{"name":..}} */
        @JsonProperty("tool_choice")
        private Object toolChoice;
    }

    /** DeepSeek 同步响应 */
    @Data
    @NoArgsConstructor
    public static class DeepSeekResponse {
        private String id;
        private String model;
        private List<Choice> choices;
        private Usage usage;

        @Data
        @NoArgsConstructor
        public static class Choice {
            private int index;
            private ChatMessage message;
            @JsonProperty("finish_reason")
            private String finishReason;
        }

        @Data
        @NoArgsConstructor
        public static class Usage {
            @JsonProperty("prompt_tokens")
            private int promptTokens;
            @JsonProperty("completion_tokens")
            private int completionTokens;
            @JsonProperty("total_tokens")
            private int totalTokens;
        }
    }

    /** DeepSeek SSE 流式响应块 */
    @Data
    @NoArgsConstructor
    public static class DeepSeekStreamChunk {
        private String id;
        private String model;
        private List<Choice> choices;

        @Data
        @NoArgsConstructor
        public static class Choice {
            private int index;
            private Delta delta;
            @JsonProperty("finish_reason")
            private String finishReason;
        }

        @Data
        @NoArgsConstructor
        public static class Delta {
            private String role;
            private String content;
        }
    }
}
