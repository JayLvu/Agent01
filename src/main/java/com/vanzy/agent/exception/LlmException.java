package com.vanzy.agent.exception;

/**
 * LLM 调用异常(网络/限流/鉴权等)
 *
 * @author VanzyLiu
 */
public class LlmException extends AgentException {

    public LlmException(String message) {
        super(502, message);
    }

    public LlmException(String message, Throwable cause) {
        super(502, message, cause);
    }
}
