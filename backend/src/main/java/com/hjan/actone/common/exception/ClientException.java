package com.hjan.actone.common.exception;

import com.hjan.actone.common.errorcode.BaseErrorCode;
import com.hjan.actone.common.errorcode.IErrorCode;

public class ClientException extends AbstractException {

    public ClientException(String message) {
        this(message, null, BaseErrorCode.CLIENT_ERROR);
    }

    public ClientException(String message, IErrorCode errorCode) {
        this(message, null, errorCode);
    }

    public ClientException(String message, Throwable throwable, IErrorCode errorCode) {
        super(message, throwable, errorCode);
    }
}

