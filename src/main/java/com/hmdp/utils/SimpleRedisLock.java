package com.hmdp.utils;

import cn.hutool.core.lang.UUID;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.concurrent.TimeUnit;

public class SimpleRedisLock implements ILock {
    private StringRedisTemplate stringRedisTemplate;
    private String lockName;
    private static final String KEY_PREFIX = "LOCK:";
    private static final String ID_PREFIX = UUID.randomUUID().toString(true)+"-";
    public SimpleRedisLock(StringRedisTemplate stringRedisTemplate, String lockName) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.lockName = lockName;
    }

    @Override
    public boolean tryLock(long timeoutSec) {
        String threadId = ID_PREFIX+Thread.currentThread().getId();
        Boolean success = stringRedisTemplate.opsForValue().setIfAbsent(KEY_PREFIX + lockName, threadId + "", timeoutSec, TimeUnit.SECONDS);
        return Boolean.TRUE.equals(success);
    }

    @Override
    public void unlock() {
        //获取线程标识
        String threadId = ID_PREFIX+Thread.currentThread().getId();
        //判断标识是否一致
        String value = stringRedisTemplate.opsForValue().get(KEY_PREFIX + lockName);
        if(threadId.equals(value)){
            //释放锁
            stringRedisTemplate.delete(KEY_PREFIX + lockName);
        }
    }
}
