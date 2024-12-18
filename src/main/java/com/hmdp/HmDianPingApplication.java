package com.hmdp;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.data.redis.core.StringRedisTemplate;

import javax.annotation.Resource;

@MapperScan("com.hmdp.mapper")
@EnableAspectJAutoProxy(exposeProxy = true)
@SpringBootApplication
public class HmDianPingApplication {

    @Resource
    private StringRedisTemplate stringRedisTemplate;
    public static void main(String[] args) {
        SpringApplication.run(HmDianPingApplication.class, args);
    }


}
