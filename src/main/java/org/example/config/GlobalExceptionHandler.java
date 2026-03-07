package org.example.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 全局 REST 异常处理：统一错误响应体为 { "message": "..." }，并设置 400/404/500。
 * 仅对 @RestController 生效，不影响返回视图的 @Controller。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private static Map<String, String> body(String message) {
        Map<String, String> map = new HashMap<>();
        map.put("message", message != null ? message : "请求失败");
        return map;
    }

    /** 业务参数/状态不合法（如「不存在」「请勿重复点击」）→ 400 */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleIllegalArgument(IllegalArgumentException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body(e.getMessage()));
    }

    /** 业务状态不允许（如未配置、状态冲突）→ 400 */
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, String>> handleIllegalState(IllegalStateException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body(e.getMessage()));
    }

    /** @Valid 校验失败（请求体参数不合法）→ 400 */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> handleValidation(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(err -> err.getField() + ": " + err.getDefaultMessage())
                .collect(Collectors.joining("; "));
        if (message.isEmpty()) {
            message = "参数校验失败";
        }
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body(message));
    }

    /** 其他未捕获异常 → 500，不向客户端暴露堆栈，仅打日志 */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleException(Exception e) {
        log.error("未捕获异常", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(body("服务异常，请稍后重试"));
    }
}
