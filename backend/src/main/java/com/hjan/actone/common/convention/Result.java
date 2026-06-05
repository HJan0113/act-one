package com.hjan.actone.common.convention;

import java.io.Serial;
import java.io.Serializable;

public class Result<T> implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    public static final String SUCCESS_CODE = "0";

    private String code;
    private String message;
    private T data;
    private String requestId;

    public String getCode() {
        return code;
    }

    public Result<T> setCode(String code) {
        this.code = code;
        return this;
    }

    public String getMessage() {
        return message;
    }

    public Result<T> setMessage(String message) {
        this.message = message;
        return this;
    }

    public T getData() {
        return data;
    }

    public Result<T> setData(T data) {
        this.data = data;
        return this;
    }

    public String getRequestId() {
        return requestId;
    }

    public Result<T> setRequestId(String requestId) {
        this.requestId = requestId;
        return this;
    }

    public boolean isSuccess() {
        return SUCCESS_CODE.equals(code);
    }
}

