package com.fdu.cloudnative.api.common;

public class Result<T> {
    private static final String SUCCESS = "SUCCESS";

    private String code;
    private String message;
    private T data;

    public static <T> Result<T> success(String message, T data) {
        Result<T> r = new Result<>();
        r.setCode(SUCCESS);
        r.setMessage(message);
        r.setData(data);
        return r;
    }

    public static <T> Result<T> success(T data) {
        return success("查询成功", data);
    }

    public static <T> Result<T> error(String code, String message) {
        return error(code, message, null);
    }

    public static <T> Result<T> error(String code, String message, T data) {
        Result<T> r = new Result<>();
        r.setCode(code);
        r.setMessage(message);
        r.setData(data);
        return r;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public T getData() {
        return data;
    }

    public void setData(T data) {
        this.data = data;
    }
}
