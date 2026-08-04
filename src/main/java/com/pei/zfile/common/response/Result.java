package com.pei.zfile.common.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Result<T> {

    private String code;
    private String message;
    private T data;
    private String requestId;
    private List<FieldError> errors;

    private Result(String code, String message, T data) {
        this.code = code;
        this.message = message;
        this.data = data;
    }

    public static <T> Result<T> success() {
        return new Result<>(ResultCode.OK.getCode(), ResultCode.OK.getMessage(), null);
    }

    public static <T> Result<T> success(T data) {
        return new Result<>(ResultCode.OK.getCode(), ResultCode.OK.getMessage(), data);
    }

    public static <T> Result<T> success(String message, T data) {
        return new Result<>(ResultCode.OK.getCode(), message, data);
    }

    public static <T> Result<T> error(String code, String message) {
        return new Result<>(code, message, null);
    }

    public static <T> Result<T> error(ResultCode resultCode) {
        return new Result<>(resultCode.getCode(), resultCode.getMessage(), null);
    }

    public static <T> Result<T> error(ResultCode resultCode, String message) {
        return new Result<>(resultCode.getCode(), message, null);
    }

    public static <T> Result<T> error(String code, String message, String requestId) {
        Result<T> result = new Result<>(code, message, null);
        result.setRequestId(requestId);
        return result;
    }

    public static <T> Result<T> validationError(List<FieldError> errors) {
        Result<T> result = new Result<>(ResultCode.VALIDATION_ERROR.getCode(),
                ResultCode.VALIDATION_ERROR.getMessage(), null);
        result.setErrors(errors);
        return result;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FieldError {
        private String field;
        private String message;
        private Object rejectedValue;
    }
}