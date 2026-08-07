package com.vanzy.agent.exception;

/**
 * Agent 业务异常基类
 *
 * @author VanzyLiu
 */
public class AgentException extends RuntimeException {

    private final int code;

    public AgentException(String message) {
        this(500, message);
    }

    public AgentException(String message, Throwable cause) {
        this(500, message, cause);
    }

    public AgentException(int code, String message) {
        super(message);
        this.code = code;
    }

    public AgentException(int code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    public int getCode() {
        return code;
    }
}
