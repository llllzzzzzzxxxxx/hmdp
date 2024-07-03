package com.hmdp.utils;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hmdp.entity.Shop;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.events.Event;

import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

@Slf4j
@Component
public class CacheClient {

    private final StringRedisTemplate stringRedisTemplate;
    public CacheClient(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }
    public void set(String key, Object value, Long time, TimeUnit unit){
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(value), time, unit);
    }
    public void setWithLogicalExpire(String key, Object value, Long time, TimeUnit unit){
        RedisData redisData = new RedisData();
        redisData.setData(value);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(unit.toSeconds(time)));
        stringRedisTemplate.opsForValue().set(key,JSONUtil.toJsonStr(redisData));
    }

    public <R,ID> R queryWithMutex(
            String keyPrefix,ID id,Class<R> type,Function<ID,R> dbFallback, Long time, TimeUnit unit) throws InterruptedException {
        String key = keyPrefix+id;
//        1、从redis查商铺缓存
        String json = stringRedisTemplate.opsForValue().get(key);
//        2、判断是否存在
        if (StrUtil.isNotBlank(json)){
//        3、存在，直接返回
            return JSONUtil.toBean(json,type);
        }
        if (json!=null){
            return null;
        }
//        4、不存在，根据id查询数据库
           R r = dbFallback.apply(id);
            Thread.sleep(200);
//        5、不存在，返回错误
            if (r == null){
                stringRedisTemplate.opsForValue().set(key,"",30L, TimeUnit.MINUTES);
                return null;
            }
//        6、存在，写入redis
            this.set(key,r,time,unit);
            //        7、返回
        return r;
    }

    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);

    public <R,ID> R queryWithLogicalExpire(String keyPrefix,Long id,Class<R> type,Function<ID,R> dbFallback, Long time, TimeUnit unit){
        String key = keyPrefix+id;
//        1、从redis查商铺缓存
        String json = stringRedisTemplate.opsForValue().get(key);
//        2、判断是否存在
        if (!StrUtil.isBlank(json)){
            return null;
        }
        RedisData redisData = JSONUtil.toBean(json, RedisData.class);
        Object data = redisData.getData();
        R r = JSONUtil.toBean((JSONObject) data, type);
        LocalDateTime expireTime = redisData.getExpireTime();
        if (expireTime.isAfter(LocalDateTime.now())){
            return r;
        }
        String lockKey = "lock:shop:"+id;
        boolean isLock = tryLock(lockKey);

        if (isLock){
            CACHE_REBUILD_EXECUTOR.submit(()->{
                try {
                    R r1 = dbFallback.apply((ID) id);
                    this.setWithLogicalExpire(key,r1,time,unit);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                } finally {
                    unlock(lockKey);
                }

            });
        }
//        6、存在，写入redis
        stringRedisTemplate.opsForValue().set(key,JSONUtil.toJsonStr(r),time,unit );
//

        return r;
    }
    private boolean tryLock(String key){
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", 10, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(flag);
    }
    private void unlock(String key){
        stringRedisTemplate.delete(key);
    }
}
