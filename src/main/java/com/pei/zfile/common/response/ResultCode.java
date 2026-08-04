package com.pei.zfile.common.response;

import lombok.Getter;

@Getter
public enum ResultCode {

    OK("OK", "success"),

    // 400
    VALIDATION_ERROR("VALIDATION_ERROR", "参数校验失败"),
    INVALID_OPERATION("INVALID_OPERATION", "非法操作"),

    // 401
    UNAUTHORIZED("UNAUTHORIZED", "未登录或令牌失效"),
    INVALID_CREDENTIALS("INVALID_CREDENTIALS", "用户名或密码错误"),

    // 403
    FORBIDDEN("FORBIDDEN", "无权限访问资源"),
    SHARE_PASSWORD_REQUIRED("SHARE_PASSWORD_REQUIRED", "分享需要口令"),
    SHARE_PASSWORD_INVALID("SHARE_PASSWORD_INVALID", "分享口令错误"),

    // 404
    USER_NOT_FOUND("USER_NOT_FOUND", "用户不存在"),
    FILE_NOT_FOUND("FILE_NOT_FOUND", "节点不存在"),
    SHARE_NOT_FOUND("SHARE_NOT_FOUND", "分享不存在"),
    RESOURCE_NOT_FOUND("RESOURCE_NOT_FOUND", "请求资源不存在"),

    // 409
    FILE_NAME_CONFLICT("FILE_NAME_CONFLICT", "目标目录中已存在同名文件"),
    USERNAME_EXISTS("USERNAME_EXISTS", "用户名已存在"),
    EMAIL_EXISTS("EMAIL_EXISTS", "邮箱已存在"),

    // 410
    SHARE_EXPIRED("SHARE_EXPIRED", "分享已过期或取消"),
    DOWNLOAD_LIMIT_REACHED("DOWNLOAD_LIMIT_REACHED", "达到下载上限"),

    // 413
    FILE_TOO_LARGE("FILE_TOO_LARGE", "文件超过限制"),

    // 422
    QUOTA_EXCEEDED("QUOTA_EXCEEDED", "用户空间不足"),

    // 415
    UNSUPPORTED_MEDIA_TYPE("UNSUPPORTED_MEDIA_TYPE", "不支持预览该文件类型"),

    // 429
    TOO_MANY_REQUESTS("TOO_MANY_REQUESTS", "请求过于频繁，请稍后重试"),

    // 500
    STORAGE_ERROR("STORAGE_ERROR", "物理存储异常"),
    INTERNAL_ERROR("INTERNAL_ERROR", "服务器内部错误");

    private final String code;
    private final String message;

    ResultCode(String code, String message) {
        this.code = code;
        this.message = message;
    }
}
