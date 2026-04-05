package com.gobang.model.dto;

import lombok.Data;

/**
 * 统一响应 DTO
 */
@Data
public class ResponseDto<T> {
    private boolean success;
    private String message;
    private T data;
    private int code;

    public static <T> ResponseDto<T> success(T data) {
        ResponseDto<T> response = new ResponseDto<>();
        response.setSuccess(true);
        response.setData(data);
        response.setCode(200);
        return response;
    }

    public static <T> ResponseDto<T> success(String message, T data) {
        ResponseDto<T> response = new ResponseDto<>();
        response.setSuccess(true);
        response.setMessage(message);
        response.setData(data);
        response.setCode(200);
        return response;
    }

    public static <T> ResponseDto<T> error(String message) {
        ResponseDto<T> response = new ResponseDto<>();
        response.setSuccess(false);
        response.setMessage(message);
        response.setCode(500);
        return response;
    }

    public static <T> ResponseDto<T> error(int code, String message) {
        ResponseDto<T> response = new ResponseDto<>();
        response.setSuccess(false);
        response.setCode(code);
        response.setMessage(message);
        return response;
    }
}
