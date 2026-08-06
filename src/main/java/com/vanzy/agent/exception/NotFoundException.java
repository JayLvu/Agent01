package com.vanzy.agent.exception;

/**
 * 资源未找到异常
 *
 * @author VanzyLiu
 */
public class NotFoundException extends AgentException {

    public NotFoundException(String message) {
        super(404, message);
    }
}
