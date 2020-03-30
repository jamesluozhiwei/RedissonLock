package com.github.jamesluozhiwei.annotation;

import com.github.jamesluozhiwei.enums.RedissonLockModel;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 分布式锁 | 注解
 * @author jamesluozhiwei
 * @date 2020/3/30 19:16
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RedissonLock {

    /**
     * 锁类型，默认自动
     * @return
     */
    RedissonLockModel lockModel() default RedissonLockModel.AUTO;

    /**
     * 需加锁的资源键 读取方法中的参数列表 支持springEL
     * @return
     */
    String[] keys() default {};

    /**
     * key的静态常量 表明key的分类 拼接与参数的前面，可用用模糊key查询删除
     * a whole key example: redisson:lock:good@1   其中good就是常量值 表明给 物品1 上锁
     * @return
     */
    String keyConstant() default "";

    /**
     * 锁超时时间，默认30000毫秒
     * @return
     */
    long lockWatchdogTimeout() default 0;

    /**
     * 等待加锁超时时间，默认10000毫秒 -1表示一直等待
     * @return
     */
    long attemptTimeout() default 10000;
}