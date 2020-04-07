package com.github.jamesluozhiwei.exception;

/**
 * 获取锁失败异常
 * @author jamesluozhiwei
 * @date 2020/4/7 20:41
 */
public class RedissonGetLockException extends RuntimeException {

    private static final long serialVersionUID = 1461676259235052360L;

    public RedissonGetLockException() {
    }

    public RedissonGetLockException(String message) {
        super(message);
    }

    public RedissonGetLockException(String message, Throwable cause) {
        super(message, cause);
    }

    public RedissonGetLockException(Throwable cause) {
        super(cause);
    }

    public RedissonGetLockException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }
}
