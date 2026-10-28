package com.campus.mall.common.exception;

import com.campus.mall.common.api.ErrorCode;
import lombok.Getter;

import java.util.HashMap;
import java.util.Map;

/**
 * 业务异常。全局异常处理器会把它转成 {@code R{code,message,data}}。
 * data 用于承载前端要用的附加信息（如 20003 的 banUntil、30009 的 publishBanUntil）。
 */
@Getter
public class BizException extends RuntimeException {

    private final int code;
    private final transient Object data;

    public BizException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.code = errorCode.getCode();
        this.data = null;
    }

    public BizException(ErrorCode errorCode, String message) {
        super(message);
        this.code = errorCode.getCode();
        this.data = null;
    }

    public BizException(ErrorCode errorCode, Object data) {
        super(errorCode.getMessage());
        this.code = errorCode.getCode();
        this.data = data;
    }

    public BizException(ErrorCode errorCode, String message, Object data) {
        super(message);
        this.code = errorCode.getCode();
        this.data = data;
    }

    public BizException(int code, String message) {
        super(message);
        this.code = code;
        this.data = null;
    }

    /** 快捷方法：带一个字段给前端读 */
    public static BizException of(ErrorCode errorCode, Map<String, Object> data) {
        return new BizException(errorCode, errorCode.getMessage(), new HashMap<>(data));
    }
}
