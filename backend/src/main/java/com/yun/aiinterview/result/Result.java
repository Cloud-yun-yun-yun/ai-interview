package com.yun.aiinterview.result;

import java.io.Serializable;
import lombok.Data;

@Data
public class Result<T> implements Serializable {
    private Integer code; //编码：1成功，0和其它数字为失败
    private String msg; //错误信息
    private T data;//数据

    public static <T> Result<T> success() {
        Result<T> result = new Result<T>();
        result.code = 1;
        return result;
    }
    public static <T> Result<T> success(T data) {
        Result<T> result = new Result<>();
        result.setCode(1);
        result.setMsg("成功");
        result.setData(data);
        return result;
    }
    public static <T> Result<T> error(String msg) {
        return error(0, msg);
    }
    public static <T> Result<T> error(Integer code) {
        return error(code, "失败");
    }
    public static <T> Result<T> error(Integer code, String msg) {
        Result<T> result = new Result<>();
        result.setCode(code);
        result.setMsg(msg);
        return result;
    }
}
