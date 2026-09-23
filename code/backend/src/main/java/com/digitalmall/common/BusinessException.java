package com.digitalmall.common;

import lombok.Getter;

/**
 * 业务异常：抛出自定义消息，由全局异常处理器统一转为 Result
 */
@Getter
public class BusinessException extends RuntimeException {

    private final int code;

    public BusinessException(int code, String message) {
        super(message);
        this.code = code;
    }

    /** 默认业务冲突 409 */
    public BusinessException(String message) {
        this(409, message);
    }
}
