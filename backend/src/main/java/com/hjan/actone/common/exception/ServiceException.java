package com.hjan.actone.common.exception;

import com.hjan.actone.common.errorcode.BaseErrorCode;
import com.hjan.actone.common.errorcode.IErrorCode;

public class ServiceException extends AbstractException {

    public ServiceException(String message) {
        this(message, null, BaseErrorCode.SERVICE_ERROR);
    }

    public ServiceException(String message, IErrorCode errorCode) {
        this(message, null, errorCode);
    }

    public ServiceException(String message, Throwable throwable, IErrorCode errorCode) {
        super(message, throwable, errorCode);
    }
}

