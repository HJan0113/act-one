package com.hjan.actone.infra.ai.error;

import com.hjan.actone.common.errorcode.BaseErrorCode;
import com.hjan.actone.common.exception.RemoteException;

public class ModelClientExceptionMapper {

    private static final String TIMEOUT_MESSAGE = "AI 模型调用超时，请稍后重试";
    private static final String RATE_LIMIT_MESSAGE = "AI 模型请求过于频繁，请稍后重试";
    private static final String CONFIGURATION_MESSAGE = "AI 模型配置不可用，请联系管理员";
    private static final String INVALID_RESPONSE_MESSAGE = "AI 模型返回内容无效，请重试";
    private static final String PROVIDER_UNAVAILABLE_MESSAGE = "AI 模型服务暂时不可用，请稍后重试";
    private static final String CLIENT_ERROR_MESSAGE = "AI 模型请求失败，请稍后重试";
    private static final String GENERIC_MESSAGE = "AI 模型调用失败，请稍后重试";

    public RemoteException map(RuntimeException exception) {
        if (exception instanceof ModelClientException clientException) {
            return mapClientException(clientException);
        }
        if (exception instanceof RemoteException remoteException
                && BaseErrorCode.SERVICE_TIMEOUT_ERROR.code().equals(remoteException.getErrorCode())) {
            return remote(TIMEOUT_MESSAGE, exception, BaseErrorCode.SERVICE_TIMEOUT_ERROR);
        }
        return remote(GENERIC_MESSAGE, exception, BaseErrorCode.REMOTE_ERROR);
    }

    private RemoteException mapClientException(ModelClientException exception) {
        ModelClientErrorType errorType = exception.getErrorType();
        if (errorType == null) {
            return remote(GENERIC_MESSAGE, exception, BaseErrorCode.REMOTE_ERROR);
        }
        return switch (errorType) {
            case TIMEOUT -> remote(TIMEOUT_MESSAGE, exception, BaseErrorCode.SERVICE_TIMEOUT_ERROR);
            case RATE_LIMITED -> remote(RATE_LIMIT_MESSAGE, exception, BaseErrorCode.REMOTE_ERROR);
            case UNAUTHORIZED, CONFIGURATION_ERROR ->
                    remote(CONFIGURATION_MESSAGE, exception, BaseErrorCode.REMOTE_ERROR);
            case INVALID_RESPONSE ->
                    remote(INVALID_RESPONSE_MESSAGE, exception, BaseErrorCode.REMOTE_ERROR);
            case NETWORK_ERROR, SERVER_ERROR, PROVIDER_ERROR ->
                    remote(PROVIDER_UNAVAILABLE_MESSAGE, exception, BaseErrorCode.REMOTE_ERROR);
            case CLIENT_ERROR ->
                    remote(CLIENT_ERROR_MESSAGE, exception, BaseErrorCode.REMOTE_ERROR);
        };
    }

    private RemoteException remote(String message,
                                   RuntimeException cause,
                                   BaseErrorCode errorCode) {
        return new RemoteException(message, cause, errorCode);
    }
}
