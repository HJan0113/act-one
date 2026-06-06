package com.hjan.actone.common.log;

import com.hjan.actone.common.exception.AbstractException;
import org.springframework.util.StringUtils;

import java.lang.reflect.Method;
import java.util.regex.Pattern;

public final class AiExceptionDiagnostics {

    private static final Pattern API_KEY_PATTERN = Pattern.compile("(?i)(api[-_]?key\\s*[=:]\\s*)\\S+");
    private static final Pattern AUTHORIZATION_PATTERN = Pattern.compile("(?i)(authorization\\s*[=:]\\s*)\\S+");
    private static final Pattern BEARER_PATTERN = Pattern.compile("(?i)bearer\\s+\\S+");
    private static final int MAX_MESSAGE_LENGTH = 240;
    private static final String MODEL_CLIENT_EXCEPTION_CLASS = "com.hjan.actone.infra.ai.error.ModelClientException";

    private AiExceptionDiagnostics() {
    }

    public static String errorType(Throwable error) {
        return error == null ? "n/a" : error.getClass().getSimpleName();
    }

    public static String errorCode(Throwable error) {
        AbstractException exception = findCause(error, AbstractException.class);
        return exception == null ? null : exception.getErrorCode();
    }

    public static String safeMessage(Throwable error) {
        Throwable current = error;
        while (current != null) {
            String message = safeMessageFor(current);
            if (StringUtils.hasText(message)) {
                return message;
            }
            current = current.getCause();
        }
        return null;
    }

    public static String modelErrorType(Throwable error) {
        Throwable exception = findModelClientException(error);
        Object value = invokeNoArg(exception, "getErrorType");
        return value == null ? null : value.toString();
    }

    public static Integer modelStatusCode(Throwable error) {
        Throwable exception = findModelClientException(error);
        Object value = invokeNoArg(exception, "getStatusCode");
        return value instanceof Integer status ? status : null;
    }

    public static String rootCauseType(Throwable error) {
        Throwable root = rootCause(error);
        return root == null ? null : root.getClass().getSimpleName();
    }

    public static String rootCauseSafeMessage(Throwable error) {
        Throwable root = rootCause(error);
        return root == null ? null : safeMessageFor(root);
    }

    public static String sanitize(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String sanitized = value.trim();
        sanitized = API_KEY_PATTERN.matcher(sanitized).replaceAll("$1***");
        sanitized = AUTHORIZATION_PATTERN.matcher(sanitized).replaceAll("$1***");
        sanitized = BEARER_PATTERN.matcher(sanitized).replaceAll("Bearer ***");
        if (sanitized.length() <= MAX_MESSAGE_LENGTH) {
            return sanitized;
        }
        return sanitized.substring(0, MAX_MESSAGE_LENGTH).stripTrailing();
    }

    public static <T extends Throwable> T findCause(Throwable error, Class<T> type) {
        Throwable current = error;
        while (current != null) {
            if (type.isInstance(current)) {
                return type.cast(current);
            }
            current = current.getCause();
        }
        return null;
    }

    private static String safeMessageFor(Throwable error) {
        if (isModelClientException(error)) {
            String message = error.getMessage();
            return isKnownSafeModelMessage(message) ? sanitize(message) : null;
        }
        if (error instanceof AbstractException abstractException) {
            String message = abstractException.getErrorMessage();
            if (error.getCause() != null || isKnownSafeBusinessMessage(message)) {
                return sanitize(message);
            }
        }
        return null;
    }

    private static boolean isKnownSafeBusinessMessage(String message) {
        if (!StringUtils.hasText(message)) {
            return false;
        }
        String value = message.trim();
        return value.startsWith("AI ")
                || value.startsWith("YAML ")
                || value.startsWith("Schema ")
                || value.startsWith("至少需要")
                || value.contains("配置不可用")
                || value.contains("调用失败")
                || value.contains("请求失败")
                || value.contains("返回内容");
    }

    private static boolean isKnownSafeModelMessage(String message) {
        if (!StringUtils.hasText(message)) {
            return false;
        }
        String value = message.trim();
        return value.startsWith("Spring AI ")
                && !value.toLowerCase().contains("body")
                && !value.toLowerCase().contains("api-key=")
                && !value.toLowerCase().contains("authorization=");
    }

    private static Throwable findModelClientException(Throwable error) {
        Throwable current = error;
        while (current != null) {
            if (isModelClientException(current)) {
                return current;
            }
            current = current.getCause();
        }
        return null;
    }

    private static boolean isModelClientException(Throwable error) {
        return error != null && MODEL_CLIENT_EXCEPTION_CLASS.equals(error.getClass().getName());
    }

    private static Object invokeNoArg(Throwable error, String methodName) {
        if (error == null) {
            return null;
        }
        try {
            Method method = error.getClass().getMethod(methodName);
            return method.invoke(error);
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    private static Throwable rootCause(Throwable error) {
        Throwable current = error;
        Throwable root = error;
        while (current != null) {
            root = current;
            current = current.getCause();
        }
        return root;
    }
}
