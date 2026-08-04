package com.erp.common;

import lombok.Getter;

/**
 * 业务异常。httpStatus 决定 HTTP 状态码：
 * 默认 200（code!=0 表示业务失败，前端弹 message）；权限/未找到等用 401/403/404。
 */
@Getter
public class BizException extends RuntimeException {

    private final int code;
    private final int httpStatus;

    public BizException(String message) {
        super(message);
        this.code = 1;
        this.httpStatus = 200;
    }

    public BizException(int httpStatus, int code, String message) {
        super(message);
        this.httpStatus = httpStatus;
        this.code = code;
    }

    public static BizException badRequest(String message) {
        return new BizException(200, 1, message);
    }

    public static BizException unauthorized(String message) {
        return new BizException(401, 401, message);
    }

    public static BizException forbidden(String message) {
        return new BizException(403, 403, message);
    }

    public static BizException notFound(String message) {
        return new BizException(404, 404, message);
    }
}
