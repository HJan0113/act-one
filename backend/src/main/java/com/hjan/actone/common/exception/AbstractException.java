package com.hjan.actone.common.exception;

import com.hjan.actone.common.errorcode.IErrorCode;
import org.springframework.util.StringUtils;

public abstract class AbstractException extends RuntimeException {

    private final String errorCode;
    private final String errorMessage;

    protected AbstractException(String message, Throwable throwable, IErrorCode errorCode) {
        super(message, throwable);
        this.errorCode = errorCode.code();
        this.errorMessage = StringUtils.hasText(message) ? message : errorCode.message();
    }

    public String getErrorCode() {
        return errorCode;
    }

    public String getErrorMessage() {
        return errorMessage;
    }
}

