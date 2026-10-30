package com.campus.mall.common.exception;

import com.campus.mall.common.api.ErrorCode;
import com.campus.mall.common.api.R;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.stream.Collectors;

/**
 * 全局异常处理。约定（03 §1.3）：参数类问题一律 10001，message 要写清哪个字段。
 * HTTP 状态码统一 200，错误靠 body 里的 code 区分（网关层 401 除外）。
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BizException.class)
    public R<Object> handleBiz(BizException e, HttpServletRequest request) {
        log.warn("[biz] {} {} -> {} {}", request.getMethod(), request.getRequestURI(), e.getCode(), e.getMessage());
        return R.fail(e.getCode(), e.getMessage(), e.getData());
    }

    @ExceptionHandler(AccessDeniedException.class)
    public R<Object> handleAccessDenied(AccessDeniedException e, HttpServletRequest request) {
        log.warn("[forbidden] {} {} -> 10003", request.getMethod(), request.getRequestURI());
        return R.fail(ErrorCode.FORBIDDEN);
    }

    @ExceptionHandler(BindException.class)
    public R<Object> handleBind(BindException e) {
        String msg = e.getBindingResult().getFieldErrors().stream()
                .map(f -> f.getField() + ": " + f.getDefaultMessage())
                .collect(Collectors.joining("; "));
        return R.fail(ErrorCode.PARAM_INVALID, msg);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public R<Object> handleConstraint(ConstraintViolationException e) {
        String msg = e.getConstraintViolations().stream()
                .map(ConstraintViolation::getMessage)
                .collect(Collectors.joining("; "));
        return R.fail(ErrorCode.PARAM_INVALID, msg);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public R<Object> handleMissingParam(MissingServletRequestParameterException e) {
        return R.fail(ErrorCode.PARAM_INVALID, "缺少参数: " + e.getParameterName());
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public R<Object> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        return R.fail(ErrorCode.PARAM_INVALID, "参数类型错误: " + e.getName());
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public R<Object> handleNotReadable(HttpMessageNotReadableException e) {
        return R.fail(ErrorCode.PARAM_INVALID, "请求体格式错误");
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public R<Object> handleMethod(HttpRequestMethodNotSupportedException e) {
        return R.fail(ErrorCode.PARAM_INVALID, "请求方法不支持: " + e.getMethod());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public R<Object> handleIllegalArgument(IllegalArgumentException e) {
        return R.fail(ErrorCode.PARAM_INVALID, e.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public R<Object> handleOther(Exception e, HttpServletRequest request) {
        log.error("[error] {} {}", request.getMethod(), request.getRequestURI(), e);
        return R.fail(ErrorCode.SERVICE_DEGRADED, "服务器内部错误");
    }
}
