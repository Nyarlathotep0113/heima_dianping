package com.hmdp.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

@Configuration
public class RedisConfig {
    @Bean
    public RedissonClient redissonClient() throws IOException {
        try(InputStream input=this.getClass().getResourceAsStream("redisson.properties")){
            Properties properties=new Properties();
            properties.load(input);
            Config config=new Config();
            config.useSingleServer().setAddress(properties.getProperty("redisson.address")).setPassword("redisson.password");
            return Redisson.create(config);
        }

    }
}
