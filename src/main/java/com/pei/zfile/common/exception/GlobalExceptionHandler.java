package com.pei.zfile.common.exception;

import com.pei.zfile.common.response.Result;
import com.pei.zfile.common.response.ResultCode;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import jakarta.validation.ConstraintViolationException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 全局异常处理器，统一拦截 Controller 层抛出的异常并转换为标准 JSON 响应。
 * <p>
 * 处理优先级（从具体到通用）：
 * <ol>
 *   <li>{@link BusinessException} —— 业务异常，根据错误码映射 HTTP 状态码</li>
 *   <li>{@link MethodArgumentNotValidException} —— {@code @Valid} 请求体校验失败</li>
 *   <li>{@link BindException} —— 表单绑定校验失败</li>
 *   <li>{@link IllegalArgumentException} —— 未替换的非法参数兜底</li>
 *   <li>{@link Exception} —— 未知异常兜底，返回 500</li>
 * </ol>
 * <p>
 * 错误码到 HTTP 状态码的映射关系见 {@link #mapHttpStatus(ResultCode)}。
 * </p>
 *
 * @see BusinessException
 * @see ResultCode
 * @see Result
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 处理业务异常，根据 {@link ResultCode} 映射为对应的 HTTP 状态码。
     * <p>
     * 日志级别为 WARN，因为业务异常属于预期内的可恢复错误。
     * </p>
     *
     * @param ex      业务异常实例
     * @param request HTTP 请求对象，用于记录请求 URI
     * @return 包含错误码和消息的标准错误响应
     */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<Result<Void>> handleBusinessException(BusinessException ex, HttpServletRequest request) {
        log.warn("业务异常 - code={}, message={}, uri={}",
                ex.getResultCode().getCode(), ex.getMessage(), request.getRequestURI());
        HttpStatus httpStatus = mapHttpStatus(ex.getResultCode());
        return ResponseEntity.status(httpStatus)
                .body(Result.error(ex.getResultCode(), ex.getMessage()));
    }

    /**
     * 处理 {@code @Valid} 注解触发的请求体校验失败。
     * <p>
     * 将 {@link org.springframework.validation.FieldError} 列表转换为
     * {@link Result.FieldError} 列表，返回 400 和字段级错误详情。
     * </p>
     *
     * @param ex      校验异常，包含字段错误列表
     * @param request HTTP 请求对象
     * @return 包含字段级错误详情的 400 响应
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Result<Void>> handleValidationException(
            MethodArgumentNotValidException ex, HttpServletRequest request) {
        List<Result.FieldError> fieldErrors = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> new Result.FieldError(fe.getField(), fe.getDefaultMessage(), fe.getRejectedValue()))
                .collect(Collectors.toList());
        log.warn("参数校验失败 - uri={}, fieldErrors={}", request.getRequestURI(), fieldErrors);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Result.validationError(fieldErrors));
    }

    /**
     * 处理表单绑定校验异常（如 {@code @ModelAttribute} 校验失败）。
     *
     * @param ex      绑定异常
     * @param request HTTP 请求对象
     * @return 包含字段级错误详情的 400 响应
     */
    @ExceptionHandler(BindException.class)
    public ResponseEntity<Result<Void>> handleBindException(BindException ex, HttpServletRequest request) {
        List<Result.FieldError> fieldErrors = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> new Result.FieldError(fe.getField(), fe.getDefaultMessage(), fe.getRejectedValue()))
                .collect(Collectors.toList());
        log.warn("参数绑定失败 - uri={}, fieldErrors={}", request.getRequestURI(), fieldErrors);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Result.validationError(fieldErrors));
    }

    /**
     * 兜底处理非法参数异常。
     * <p>
     * 建议所有业务代码使用 {@link BusinessException} 替代 {@link IllegalArgumentException}，
     * 此处理器仅作为过渡期兜底，避免未替换的异常直接暴露给前端。
     * </p>
     *
     * @param ex      非法参数异常
     * @param request HTTP 请求对象
     * @return 400 错误响应
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Result<Void>> handleIllegalArgumentException(
            IllegalArgumentException ex, HttpServletRequest request) {
        log.warn("非法参数异常(兜底) - uri={}, message={}", request.getRequestURI(), ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Result.error(ResultCode.INVALID_OPERATION, ex.getMessage()));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Result<Void>> handleConstraintViolation(
            ConstraintViolationException ex, HttpServletRequest request) {
        log.warn("请求参数校验失败 - uri={}, message={}", request.getRequestURI(), ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Result.error(ResultCode.VALIDATION_ERROR, ex.getMessage()));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<Result<Void>> handleMaxUploadSizeExceeded(
            MaxUploadSizeExceededException ex, HttpServletRequest request) {
        log.warn("上传文件超过限制 - uri={}", request.getRequestURI());
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                .body(Result.error(ResultCode.FILE_TOO_LARGE));
    }

    @ExceptionHandler({
            HttpMessageNotReadableException.class,
            MethodArgumentTypeMismatchException.class,
            ServletRequestBindingException.class
    })
    public ResponseEntity<Result<Void>> handleMalformedRequest(Exception ex, HttpServletRequest request) {
        log.warn("请求格式错误 - uri={}, message={}", request.getRequestURI(), ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Result.error(ResultCode.VALIDATION_ERROR, "请求参数或消息体格式不正确"));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<Result<Void>> handleNoResourceFound(
            NoResourceFoundException ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Result.error(ResultCode.RESOURCE_NOT_FOUND));
    }

    /**
     * 处理所有未捕获的异常，统一返回 500 内部错误。
     * <p>
     * 日志级别为 ERROR，因为此类异常属于非预期错误，需要开发人员介入排查。
     * 不向前端暴露异常堆栈，仅返回通用错误码 {@code INTERNAL_ERROR}。
     * </p>
     *
     * @param ex      未捕获的异常
     * @param request HTTP 请求对象
     * @return 500 通用错误响应
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Result<Void>> handleUnknownException(Exception ex, HttpServletRequest request) {
        log.error("未捕获异常 - uri={}", request.getRequestURI(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Result.error(ResultCode.INTERNAL_ERROR));
    }

    /**
     * 将 {@link ResultCode} 映射为对应的 HTTP 状态码。
     * <p>
     * 映射关系与 {@code zfile-lite-development-plan.md} 第 6.3 节一致：
     * </p>
     * <table>
     *   <tr><th>ResultCode</th><th>HTTP Status</th></tr>
     *   <tr><td>VALIDATION_ERROR / INVALID_OPERATION</td><td>400 Bad Request</td></tr>
     *   <tr><td>UNAUTHORIZED / INVALID_CREDENTIALS</td><td>401 Unauthorized</td></tr>
     *   <tr><td>FORBIDDEN / SHARE_PASSWORD_*</td><td>403 Forbidden</td></tr>
     *   <tr><td>USER_NOT_FOUND / FILE_NOT_FOUND / SHARE_NOT_FOUND</td><td>404 Not Found</td></tr>
     *   <tr><td>FILE_NAME_CONFLICT / USERNAME_EXISTS / EMAIL_EXISTS</td><td>409 Conflict</td></tr>
     *   <tr><td>SHARE_EXPIRED / DOWNLOAD_LIMIT_REACHED</td><td>410 Gone</td></tr>
     *   <tr><td>FILE_TOO_LARGE</td><td>413 Payload Too Large</td></tr>
     *   <tr><td>QUOTA_EXCEEDED</td><td>422 Unprocessable Entity</td></tr>
     *   <tr><td>TOO_MANY_REQUESTS</td><td>429 Too Many Requests</td></tr>
     *   <tr><td>STORAGE_ERROR / INTERNAL_ERROR</td><td>500 Internal Server Error</td></tr>
     *   <tr><td>OK</td><td>200 OK</td></tr>
     * </table>
     *
     * @param resultCode 业务错误码枚举
     * @return 对应的 HTTP 状态码
     */
    private HttpStatus mapHttpStatus(ResultCode resultCode) {
        return switch (resultCode) {
            case OK -> HttpStatus.OK;
            case VALIDATION_ERROR, INVALID_OPERATION -> HttpStatus.BAD_REQUEST;
            case UNAUTHORIZED, INVALID_CREDENTIALS -> HttpStatus.UNAUTHORIZED;
            case FORBIDDEN, SHARE_PASSWORD_REQUIRED, SHARE_PASSWORD_INVALID -> HttpStatus.FORBIDDEN;
            case USER_NOT_FOUND, FILE_NOT_FOUND, SHARE_NOT_FOUND, RESOURCE_NOT_FOUND -> HttpStatus.NOT_FOUND;
            case FILE_NAME_CONFLICT, USERNAME_EXISTS, EMAIL_EXISTS -> HttpStatus.CONFLICT;
            case SHARE_EXPIRED, DOWNLOAD_LIMIT_REACHED -> HttpStatus.GONE;
            case FILE_TOO_LARGE -> HttpStatus.PAYLOAD_TOO_LARGE;
            case QUOTA_EXCEEDED -> HttpStatus.UNPROCESSABLE_ENTITY;
            case UNSUPPORTED_MEDIA_TYPE -> HttpStatus.UNSUPPORTED_MEDIA_TYPE;
            case TOO_MANY_REQUESTS -> HttpStatus.TOO_MANY_REQUESTS;
            case STORAGE_ERROR, INTERNAL_ERROR -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
    }
}
