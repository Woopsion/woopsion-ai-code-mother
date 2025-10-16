package com.woopsion.woopsionaicodemother.common;

import com.woopsion.woopsionaicodemother.exception.ErrorCode;
import lombok.Data;

import java.io.Serializable;

/**
 * @author wangpengcan
 * @date 2025/10/15
 * @time 09:16
 * @description
 */
@Data
public class BaseResponse<T> implements Serializable {

    private int code;

    private T data;

    private String message;

    public BaseResponse(int code, T data, String message) {
        this.code = code;
        this.data = data;
        this.message = message;
    }

    public BaseResponse(int code, T data) {
        this(code, data, "");
    }

    public BaseResponse(ErrorCode errorCode) {
        this(errorCode.getCode(), null, errorCode.getMessage());
    }
}
