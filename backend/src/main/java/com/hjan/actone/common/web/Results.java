package com.hjan.actone.common.web;

import com.hjan.actone.common.convention.Result;
import com.hjan.actone.common.errorcode.BaseErrorCode;
import com.hjan.actone.common.exception.AbstractException;

public final class Results {

    private Results() {
    }

    public static Result<Void> success() {
        return new Result<Void>().setCode(Result.SUCCESS_CODE);
    }

    public static <T> Result<T> success(T data) {
        return new Result<T>().setCode(Result.SUCCESS_CODE).setData(data);
    }

    public static Result<Void> failure() {
        return failure(BaseErrorCode.SERVICE_ERROR.code(), BaseErrorCode.SERVICE_ERROR.message());
    }

    public static Result<Void> failure(AbstractException exception) {
        return failure(exception.getErrorCode(), exception.getErrorMessage());
    }

    public static Result<Void> failure(String errorCode, String errorMessage) {
        return new Result<Void>().setCode(errorCode).setMessage(errorMessage);
    }
}

