package com.hjan.actone.common.web;

import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.exception.NotRoleException;
import com.hjan.actone.common.convention.Result;
import com.hjan.actone.common.errorcode.BaseErrorCode;
import com.hjan.actone.common.exception.AbstractException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result<Void> validExceptionHandler(HttpServletRequest request, MethodArgumentNotValidException ex) {
        FieldError first = ex.getBindingResult().getFieldErrors().stream().findFirst().orElse(null);
        String message = first == null ? BaseErrorCode.CLIENT_ERROR.message() : first.getDefaultMessage();
        log.warn("[{}] {} validation failed: {}", request.getMethod(), getUrl(request), message);
        return Results.failure(BaseErrorCode.CLIENT_ERROR.code(), message);
    }

    @ExceptionHandler(AbstractException.class)
    public Result<Void> abstractException(HttpServletRequest request, AbstractException ex) {
        log.warn("[{}] {} business error: {}", request.getMethod(), getUrl(request), ex.getErrorMessage(), ex);
        return Results.failure(ex);
    }

    @ExceptionHandler(NotLoginException.class)
    public Result<Void> notLoginException(HttpServletRequest request, NotLoginException ex) {
        log.warn("[{}] {} auth failed: {}", request.getMethod(), getUrl(request), ex.getMessage());
        return Results.failure(BaseErrorCode.CLIENT_ERROR.code(), "未登录或登录已过期");
    }

    @ExceptionHandler(NotRoleException.class)
    public Result<Void> notRoleException(HttpServletRequest request, NotRoleException ex) {
        log.warn("[{}] {} role denied: {}", request.getMethod(), getUrl(request), ex.getMessage());
        return Results.failure(BaseErrorCode.CLIENT_ERROR.code(), "权限不足");
    }

    @ExceptionHandler(Throwable.class)
    public Result<Void> defaultErrorHandler(HttpServletRequest request, Throwable throwable) {
        log.error("[{}] {} unexpected error", request.getMethod(), getUrl(request), throwable);
        return Results.failure();
    }

    private String getUrl(HttpServletRequest request) {
        String query = request.getQueryString();
        String url = request.getRequestURL().toString();
        return query == null || query.isBlank() ? url : url + "?" + query;
    }
}
