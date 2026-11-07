package com.yun.aiinterview.handler;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public class BusinessException extends RuntimeException {

    private final Integer code;
    private final HttpStatus httpStatus;

    public BusinessException(HttpStatus httpStatus, Integer code, String message) {
        super(message);
        this.httpStatus = httpStatus;
        this.code = code;
    }

    public BusinessException(Integer code, String message) {
        this(HttpStatus.INTERNAL_SERVER_ERROR, code, message);
    }

    public BusinessException(HttpStatus httpStatus, String message) {
        this(httpStatus, httpStatus.value(), message);
    }

    public BusinessException(String message) {
        this(HttpStatus.INTERNAL_SERVER_ERROR, 0, message);
    }

    public BusinessException(HttpStatus httpStatus, Integer code, String message, Throwable cause) {
        super(message, cause);
        this.httpStatus = httpStatus;
        this.code = code;
    }
}