package com.hjan.actone.infra.ai.error;

public enum ModelClientErrorType {
    UNAUTHORIZED,
    RATE_LIMITED,
    SERVER_ERROR,
    CLIENT_ERROR,
    TIMEOUT,
    NETWORK_ERROR,
    INVALID_RESPONSE,
    CONFIGURATION_ERROR,
    PROVIDER_ERROR;

    public static ModelClientErrorType fromHttpStatus(int status) {
        if (status == 401 || status == 403) {
            return UNAUTHORIZED;
        }
        if (status == 429) {
            return RATE_LIMITED;
        }
        if (status >= 500) {
            return SERVER_ERROR;
        }
        return CLIENT_ERROR;
    }
}
