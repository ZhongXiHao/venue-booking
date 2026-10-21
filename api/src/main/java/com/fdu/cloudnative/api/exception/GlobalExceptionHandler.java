package com.fdu.cloudnative.api.exception;

import com.fdu.cloudnative.api.common.Result;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import tools.jackson.core.JacksonException;

import java.util.List;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * 处理业务异常
     */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<Result<?>> handleBusinessException(BusinessException ex) {
        ErrorCode errorCode = ex.getErrorCode();
        return ResponseEntity.status(errorCode.getStatus())
                .body(Result.error(errorCode.name(), ex.getMessage()));
    }

    /**
     * 请求体字段校验失败（@Valid）
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Result<?>> handleMethodArgumentNotValidException(MethodArgumentNotValidException ex) {
        List<Map<String, String>> fieldErrors = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> Map.of("field", e.getField(), "message", String.valueOf(e.getDefaultMessage())))
                .toList();
        return validationError(fieldErrors);
    }

    /**
     * 请求体无法解析：JSON 损坏、日期时间格式错误等；能定位到字段时返回字段路径
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Result<?>> handleHttpMessageNotReadableException(HttpMessageNotReadableException ex) {
        String field = "body";
        String message = "请求体格式错误";
        if (ex.getCause() instanceof JacksonException je && !je.getPath().isEmpty()) {
            field = fieldPath(je);
            message = "字段格式错误";
        }
        return validationError(List.of(Map.of("field", field, "message", message)));
    }

    /**
     * 查询参数类型错误，如 page=abc、reservationDate 格式错误
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Result<?>> handleMethodArgumentTypeMismatchException(MethodArgumentTypeMismatchException ex) {
        return validationError(List.of(Map.of("field", ex.getName(), "message", "参数格式错误")));
    }

    /**
     * 查询参数校验失败，如 page < 1、pageSize 超范围
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Result<?>> handleConstraintViolationException(ConstraintViolationException ex) {
        List<Map<String, String>> fieldErrors = ex.getConstraintViolations().stream()
                .map(v -> Map.of("field", v.getPropertyPath().toString(), "message", v.getMessage()))
                .toList();
        return validationError(fieldErrors);
    }

    /**
     * 路径不存在，保持 404
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<Result<?>> handleNoResourceFoundException(NoResourceFoundException ex) {
        return simpleError(ErrorCode.NOT_FOUND);
    }

    /**
     * 请求方法不支持，保持 405
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<Result<?>> handleHttpRequestMethodNotSupportedException(HttpRequestMethodNotSupportedException ex) {
        return simpleError(ErrorCode.METHOD_NOT_ALLOWED);
    }

    /**
     * 兜底：堆栈只写日志，不返回给调用方
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Result<?>> handleException(Exception ex) {
        logger.error("未处理的异常", ex);
        return simpleError(ErrorCode.INTERNAL_ERROR);
    }

    private ResponseEntity<Result<?>> simpleError(ErrorCode errorCode) {
        return ResponseEntity.status(errorCode.getStatus())
                .body(Result.error(errorCode.name(), errorCode.getDefaultMessage()));
    }

    // 把 Jackson 的出错路径拼成 reservation.startTime、items[0].name 这样的字段名
    private String fieldPath(JacksonException je) {
        StringBuilder sb = new StringBuilder();
        for (JacksonException.Reference ref : je.getPath()) {
            if (ref.getPropertyName() != null) {
                if (!sb.isEmpty()) {
                    sb.append('.');
                }
                sb.append(ref.getPropertyName());
            } else {
                sb.append('[').append(ref.getIndex()).append(']');
            }
        }
        return sb.toString();
    }

    private ResponseEntity<Result<?>> validationError(List<Map<String, String>> fieldErrors) {
        ErrorCode errorCode = ErrorCode.VALIDATION_ERROR;
        return ResponseEntity.status(errorCode.getStatus())
                .body(Result.error(errorCode.name(), errorCode.getDefaultMessage(),
                        Map.of("fieldErrors", fieldErrors)));
    }
}
