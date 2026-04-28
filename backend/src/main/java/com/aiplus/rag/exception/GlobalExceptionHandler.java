package com.aiplus.rag.exception;

import com.aiplus.rag.model.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotWritableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(FileParseException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponse<Void> handleFileParseException(FileParseException e) {
        log.warn("文件解析异常: {}", e.getMessage());
        return ApiResponse.error(400, e.getMessage());
    }

    @ExceptionHandler(SessionNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ApiResponse<Void> handleSessionNotFoundException(SessionNotFoundException e) {
        log.warn("会话不存在: {}", e.getMessage());
        return ApiResponse.error(404, e.getMessage());
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    @ResponseStatus(HttpStatus.PAYLOAD_TOO_LARGE)
    public ApiResponse<Void> handleMaxUploadSizeExceededException(MaxUploadSizeExceededException e) {
        log.warn("文件大小超限: {}", e.getMessage());
        return ApiResponse.error(413, "文件大小超出限制（最大 10MB）");
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponse<Void> handleIllegalArgumentException(IllegalArgumentException e) {
        log.warn("参数错误: {}", e.getMessage());
        return ApiResponse.error(400, e.getMessage());
    }

    @ExceptionHandler(HttpMessageNotWritableException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ApiResponse<Void> handleHttpMessageNotWritableException(HttpMessageNotWritableException e) {
        log.error("JSON序列化失败（可能是SSE响应类型不匹配）", e);
        return ApiResponse.error(500, "SSE stream error: " + getCauseMessage(e));
    }

    @ExceptionHandler(RuntimeException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ApiResponse<Void> handleRuntimeException(RuntimeException e) {
        log.error("运行时异常", e);
        return ApiResponse.error(500, getCauseMessage(e));
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ApiResponse<Void> handleException(Exception e) {
        log.error("服务器内部错误", e);
        return ApiResponse.error(500, "服务器内部错误: " + getCauseMessage(e));
    }

    private String getCauseMessage(Throwable e) {
        Throwable cause = e.getCause();
        if (cause != null) {
            StringBuilder sb = new StringBuilder(e.getClass().getSimpleName()).append(": ").append(e.getMessage());
            while (cause != null) {
                sb.append(" | Caused by: ").append(cause.getClass().getSimpleName())
                  .append(": ").append(cause.getMessage());
                cause = cause.getCause();
            }
            return sb.toString();
        }
        return e.getClass().getSimpleName() + ": " + e.getMessage();
    }
}
