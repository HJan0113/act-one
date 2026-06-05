package com.hjan.actone.infra.ai.error;

public class ModelClientException extends RuntimeException {

    private final ModelClientErrorType errorType;
    private final Integer statusCode;

    public ModelClientException(String message,
                                ModelClientErrorType errorType,
                                Integer statusCode) {
        this(message, errorType, statusCode, null);
    }

    public ModelClientException(String message,
                                ModelClientErrorType errorType,
                                Integer statusCode,
                                Throwable cause) {
        super(message, cause);
        this.errorType = errorType;
        this.statusCode = statusCode;
    }

    public ModelClientErrorType getErrorType() {
        return errorType;
    }

    public Integer getStatusCode() {
        return statusCode;
    }
}
