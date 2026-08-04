package com.pei.zfile.common.exception;

import com.pei.zfile.common.response.ResultCode;
import lombok.Getter;

/**
 * 业务异常基类，所有业务层异常应抛出此异常。
 * <p>
 * 携带 {@link ResultCode} 错误码，由 {@link GlobalExceptionHandler} 统一拦截
 * 并转换为标准 JSON 错误响应。支持覆盖默认错误消息和包装原始异常。
 * </p>
 *
 * <p>使用示例：</p>
 * <pre>{@code
 * // 使用默认错误码和消息
 * throw new BusinessException(ResultCode.USER_NOT_FOUND);
 *
 * // 自定义错误消息
 * throw new BusinessException(ResultCode.FORBIDDEN, "用户已被禁用");
 *
 * // 包装原始异常
 * throw new BusinessException(ResultCode.STORAGE_ERROR, e);
 * }</pre>
 *
 * @see ResultCode
 * @see GlobalExceptionHandler
 */
@Getter
public class BusinessException extends RuntimeException {

    /** 业务错误码，对应 {@link ResultCode} 枚举值 */
    private final ResultCode resultCode;

    /**
     * 使用默认错误码和消息构造业务异常。
     *
     * @param resultCode 错误码枚举，消息取自 {@link ResultCode#getMessage()}
     */
    public BusinessException(ResultCode resultCode) {
        super(resultCode.getMessage());
        this.resultCode = resultCode;
    }

    /**
     * 使用自定义消息覆盖错误码默认消息。
     *
     * @param resultCode 错误码枚举
     * @param message    自定义错误消息，覆盖枚举中的默认消息
     */
    public BusinessException(ResultCode resultCode, String message) {
        super(message);
        this.resultCode = resultCode;
    }

    /**
     * 携带原始异常构造业务异常，用于保留异常链。
     *
     * @param resultCode 错误码枚举
     * @param cause      原始异常，如 IOException、SQLException 等
     */
    public BusinessException(ResultCode resultCode, Throwable cause) {
        super(resultCode.getMessage(), cause);
        this.resultCode = resultCode;
    }

    /**
     * 携带自定义消息和原始异常构造业务异常。
     *
     * @param resultCode 错误码枚举
     * @param message    自定义错误消息
     * @param cause      原始异常
     */
    public BusinessException(ResultCode resultCode, String message, Throwable cause) {
        super(message, cause);
        this.resultCode = resultCode;
    }
}