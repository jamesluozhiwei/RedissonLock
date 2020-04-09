package com.github.jamesluozhiwei.aop;

import com.github.jamesluozhiwei.annotation.RedissonLock;
import com.github.jamesluozhiwei.enums.RedissonLockModel;
import com.github.jamesluozhiwei.exception.RedissonGetLockException;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.redisson.RedissonMultiLock;
import org.redisson.RedissonRedLock;
import org.redisson.api.RLock;
import org.redisson.api.RReadWriteLock;
import org.redisson.api.RedissonClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.LocalVariableTableParameterNameDiscoverer;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 分布式锁切面实现
 * @author jamesluozhiwei
 * @date 2020/4/9 19:49
 */
@Aspect
@Component
public class RedissonLockAop {

    private static final Logger log = LoggerFactory.getLogger("redissonLockLog");

    @Resource
    private RedissonClient redissonClient;

    /**
     * 通过spring Spel 获取参数
     * @param key               定义的key值 以#开头 例如:#user
     * @param parameterNames    形参
     * @param values            形参值
     * @param keyConstant       key的常量
     * @return
     */
    public List<String> getValueBySpel(String key, String[] parameterNames, Object[] values, String keyConstant) {
        List<String> keys=new ArrayList<>();
        keyConstant =  keyConstant + "@" ;
        //RedissonAutoConfiguration
        String keyPrefix = "redisson:lock:";
        if(!key.contains("#")){
            String s = keyPrefix + keyConstant + key;
            log.info("没有使用spel表达式value->s");
            keys.add(s);
            return keys;
        }
        //spel解析器
        ExpressionParser parser = new SpelExpressionParser();
        //spel上下文
        EvaluationContext context = new StandardEvaluationContext();
        for (int i = 0; i < parameterNames.length; i++) {
            context.setVariable(parameterNames[i], values[i]);
        }
        Expression expression = parser.parseExpression(key);
        Object value = expression.getValue(context);
        if(value!=null){
            if(value instanceof Collection){
                for (Object o :  (Collection) value) {
                    keys.add(keyPrefix  + keyConstant + o.hashCode());
                }
            }else if(value.getClass().isArray()){
                Object[] obj= (Object[]) value;
                for (Object o : obj) {
                    keys.add(keyPrefix + keyConstant + o.hashCode());
                }
            }else {
                keys.add(keyPrefix + keyConstant + value.hashCode());
            }
        }
        log.info("spel表达式key={},value={}",key,keys);
        return keys;
    }

    /**
     * 环绕切面
     * @param proceedingJoinPoint
     * @return
     * @throws Throwable
     */
    @Around(value = "@annotation(com.github.jamesluozhiwei.annotation.RedissonLock)")
    public Object aroundAdvice(ProceedingJoinPoint proceedingJoinPoint) throws Throwable {
        //目标类
        Object target = proceedingJoinPoint.getTarget();
        //目标方法
        MethodSignature methodSignature = (MethodSignature) proceedingJoinPoint.getSignature();
        Method method = target.getClass().getMethod(methodSignature.getName(),methodSignature.getParameterTypes());
        RedissonLock lock = method.getAnnotation(RedissonLock.class);
        if (lock == null){
            return proceedingJoinPoint.proceed();
        }
        String[] keys = lock.keys();
        if (keys.length == 0) {
            throw new RuntimeException("keys不能为空");
        }
        String[] parameterNames = new LocalVariableTableParameterNameDiscoverer().getParameterNames(((MethodSignature) proceedingJoinPoint.getSignature()).getMethod());
        Object[] args = proceedingJoinPoint.getArgs();

        long attemptTimeout = lock.attemptTimeout();
        long lockWatchdogTimeout = lock.lockWatchdogTimeout();
        if (lockWatchdogTimeout == 0) {
            lockWatchdogTimeout = redissonClient.getConfig().getLockWatchdogTimeout();
        }
        RedissonLockModel lockModel = lock.lockModel();
        if (lockModel.equals(RedissonLockModel.AUTO)) {
            if (keys.length > 1) {
                lockModel = RedissonLockModel.RED_LOCK;
            } else {
                lockModel = RedissonLockModel.REENTRANT;
            }
        }
        if (!lockModel.equals(RedissonLockModel.MULTIPLE) && !lockModel.equals(RedissonLockModel.RED_LOCK) && keys.length > 1) {
            throw new RuntimeException("参数有多个,锁模式为->" + lockModel.name() + ".无法锁定");
        }
        log.info("锁模式->{},等待锁定时间->{}秒.锁定最长时间->{}秒",lockModel.name(),attemptTimeout/1000,lockWatchdogTimeout/1000);
        boolean res = false;
        RLock rLock = null;
        //一直等待加锁.
        switch (lockModel) {
            case FAIR:
                rLock = redissonClient.getFairLock(getValueBySpel(keys[0],parameterNames,args,lock.keyConstant()).get(0));
                break;
            case RED_LOCK:
                List<RLock> rLocks=new ArrayList<>();
                for (String key : keys) {
                    List<String> valueBySpel = getValueBySpel(key, parameterNames, args, lock.keyConstant());
                    for (String s : valueBySpel) {
                        rLocks.add(redissonClient.getLock(s));
                    }
                }
                RLock[] locks=new RLock[rLocks.size()];
                int index=0;
                for (RLock r : rLocks) {
                    locks[index++]=r;
                }
                rLock = new RedissonRedLock(locks);
                break;
            case MULTIPLE:
                rLocks=new ArrayList<>();

                for (String key : keys) {
                    List<String> valueBySpel = getValueBySpel(key, parameterNames, args, lock.keyConstant());
                    for (String s : valueBySpel) {
                        rLocks.add(redissonClient.getLock(s));
                    }
                }
                locks=new RLock[rLocks.size()];
                index=0;
                for (RLock r : rLocks) {
                    locks[index++]=r;
                }
                rLock = new RedissonMultiLock(locks);
                break;
            case REENTRANT:
                List<String> valueBySpel = getValueBySpel(keys[0], parameterNames, args, lock.keyConstant());
                //如果spel表达式是数组或者LIST 则使用红锁
                if(valueBySpel.size()==1){
                    rLock= redissonClient.getLock(valueBySpel.get(0));
                }else {
                    locks=new RLock[valueBySpel.size()];
                    index=0;
                    for (String s : valueBySpel) {
                        locks[index++]=redissonClient.getLock(s);
                    }
                    rLock = new RedissonRedLock(locks);
                }
                break;
            case READ:
                RReadWriteLock rwlock = redissonClient.getReadWriteLock(getValueBySpel(keys[0],parameterNames,args, lock.keyConstant()).get(0));
                rLock = rwlock.readLock();
                break;
            case WRITE:
                RReadWriteLock rwlock1 = redissonClient.getReadWriteLock(getValueBySpel(keys[0],parameterNames,args, lock.keyConstant()).get(0));
                rLock = rwlock1.writeLock();
                break;
            default:
                break;
        }

        //执行
        if(rLock!=null) {
            try {
                if (attemptTimeout == -1) {
                    res = true;
                    //一直等待加锁
                    rLock.lock(lockWatchdogTimeout, TimeUnit.MILLISECONDS);
                } else {
                    res = rLock.tryLock(attemptTimeout, lockWatchdogTimeout, TimeUnit.MILLISECONDS);
                }
                if (res) {
                    return proceedingJoinPoint.proceed();
                }else{
                    throw new RedissonGetLockException("获取锁失败");
                }
            } finally {
                if (res) {
                    rLock.unlock();
                }
            }
        }
        throw new RedissonGetLockException("获取锁失败");
    }

}
