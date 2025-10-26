package com.hmdp.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class RedisHotData<T> {
    private LocalDateTime expireTime;
    private T data;
}
