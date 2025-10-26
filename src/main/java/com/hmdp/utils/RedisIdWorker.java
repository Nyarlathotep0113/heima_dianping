package com.hmdp.utils;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

@Component
public class RedisIdWorker {
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    private static final long BEGIN_TIMESTAMP = 1735689600;
    private static final int SEQUENCE_BITS = 32;
    private static final long MAX_VALUE = -1L ^ (-1L << SEQUENCE_BITS);
    public long nextId(String keyPrefix){
        //1.生成时间戳

        //3.拼接字符串
        LocalDateTime now=LocalDateTime.now();
        long nowSecond=now.toEpochSecond(ZoneOffset.UTC);
        long timestamp=(nowSecond-BEGIN_TIMESTAMP);
        //2.生成序列号
        String date=now.format(DateTimeFormatter.ofPattern("yyyy:MM:dd"));
        long increment = stringRedisTemplate.opsForValue().increment("icr:" + keyPrefix + ":" + date);
        return timestamp << SEQUENCE_BITS | increment;
    }
}
