package com.hmdp.utils;


import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hmdp.entity.Shop;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static com.hmdp.utils.RedisConstants.*;

@Slf4j
@Component
public class CacheClient {

    private final StringRedisTemplate redisTemplate;
    private final static ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);


    public CacheClient(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public void set(String key, Object value, Long time, TimeUnit timeUnit) {
        redisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(value), time, timeUnit);
    }

    public void setWithLogicalExpire(String key, Object value, Long time, TimeUnit timeUnit) {
        RedisData redisData = new RedisData();
        redisData.setData(value);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(timeUnit.toSeconds(time)));

        redisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(redisData));
    }

    public <T, ID> T queryByIdWithPassThrough(String keyPrefix, ID id, Class<T> type, Function<ID, T> dbFallback, Long time, TimeUnit timeUnit) {
        String key = keyPrefix + id;
        String json = redisTemplate.opsForValue().get(key);

        if (StrUtil.isNotBlank(json)) return JSONUtil.toBean(json, type);
        if (json != null) return null;


        // mysql query

        T t = dbFallback.apply(id);
        if (t == null) {
            this.set(key, "", time, timeUnit);
            return null;
        }

        // set null in redis for id of empty data
        this.set(key, JSONUtil.toJsonStr(t), time, timeUnit);

        return t;
    }

    public <T, ID> T queryByIdWithLogicalExpire(String keyPrefix, String lockKeyPrefix, ID id, Class<T> type, Function<ID, T> dbFallback, Long time, TimeUnit timeUnit) {
        String key = keyPrefix + id;
        String shopJson = redisTemplate.opsForValue().get(key);

        if (StrUtil.isBlank(shopJson))
            return null;

        RedisData redisData = JSONUtil.toBean(shopJson, RedisData.class);

        T t = JSONUtil.toBean((JSONObject) redisData.getData(), type);

        LocalDateTime expireTime = redisData.getExpireTime();
        if (expireTime.isAfter(LocalDateTime.now())) {
            return t;
        }

        // expired

        String lockKey = lockKeyPrefix + id;
        boolean isLock = tryLock(lockKey);
        if (isLock) {
            CACHE_REBUILD_EXECUTOR.submit(() -> {
                try {
                    T t1 = dbFallback.apply(id);
                    this.setWithLogicalExpire(key, t1, time, timeUnit);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                } finally {
                    unlock(lockKey);
                }
            });
        }
        return t;

    }

    private boolean tryLock(String key) {
        Boolean flag = redisTemplate.opsForValue().setIfAbsent(key, "1", LOCK_SHOP_TTL, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(flag);
    }

    private void unlock(String key) {
        redisTemplate.delete(key);
    }

    public <T, ID> T queryByIdWithMutex(String keyPrefix, String lockKeyPrefix, ID id, Class<T> type, Function<ID, T> dbFallback, Long time, TimeUnit timeUnit) {
        String key = keyPrefix + id;
        String json = redisTemplate.opsForValue().get(key);

        if (StrUtil.isNotBlank(json))
            return JSONUtil.toBean(json, type);

        // redis hit null
        if (json != null)
            return null;

        // mysql query
        // lock
        T t;
        String lockKey = lockKeyPrefix + id;
        try {
            boolean isLock = tryLock(lockKey);
            if (!isLock) {
                Thread.sleep(50);
                return queryByIdWithMutex(keyPrefix, lockKeyPrefix, id, type, dbFallback, time, timeUnit);

            }
            t = dbFallback.apply(id);

            // mysql hit null
            if (t == null) {
                set(key, "", time, timeUnit);
                return null;
            }

            set(key, JSONUtil.toJsonStr(t), time, timeUnit);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            unlock(lockKey);
        }
        return t;
    }


}
