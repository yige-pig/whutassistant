package com.hmdp.utils;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static com.hmdp.utils.RedisConstants.*;

@Component
public class CacheClient {

    private StringRedisTemplate stringRedisTemplate;
    /**
     * 线程池
     */
    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);

    public CacheClient(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    //缓存并设置过期时间
    public void set(String key, Object value, Long time, TimeUnit timeUnit){
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(value), time, timeUnit);
    }

    //解决缓存穿透
    public <R, ID> R setWithPassThrough(String keyPrefix, ID id, Class<R> type, Function<ID, R> dbFallback,
                                        Long time, TimeUnit unit){
        //查询缓存
        String json = stringRedisTemplate.opsForValue().get(keyPrefix + id);
        R r = null;
        //缓存非空
        if(StrUtil.isNotBlank(json)){
            r = JSONUtil.toBean(json, type);
            return r;
        }
        //缓存为空字符串对象
        if(json!=null){
            return null;
        }
        //缓存不存在
        //查询数据库
        r = dbFallback.apply(id);
        if(r==null){
            //数据库没有该对象，缓存空字符串，时间不能设置太长
            set(keyPrefix+id, "", time, unit);
            return null;
        }
        //写入缓存
        set(keyPrefix+id, r, time, unit);
        return r;
    }

    //互斥锁解决缓存击穿
    public<R, ID> R queryWithMutex(String keyPrefix, ID id, Class<R> type,
                                   Function<ID, R> dbFallback, Long time, TimeUnit unit) {
        //查询缓存
        String json = stringRedisTemplate.opsForValue().get(keyPrefix + id);
        R r = null;
        //缓存非空
        if(StrUtil.isNotBlank(json)){
            r = JSONUtil.toBean(json, type);
            return r;
        }
        //缓存为空字符串对象
        if(json!=null){
            return null;
        }
        //缓存不存在，重新构建缓存
        try {
            //尝试获取锁
            Boolean lock = tryLock(LOCK_SHOP_KEY+id);
            //获取锁失败
            if(!lock){
                Thread.sleep(50);
                return queryWithMutex(keyPrefix, id, type, dbFallback, time, unit);
            }
            //查询数据库
            r = dbFallback.apply(id);
            if(r==null){
                //数据库没有该对象，缓存空字符串，时间不能设置太长
                set(keyPrefix+id, "", time, unit);
                return null;
            }
            //写入缓存
            set(keyPrefix+id, r, time, unit);
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            unlock(LOCK_SHOP_KEY+id);
        }

        return r;
    }

    //逻辑过期解决缓存击穿
    public <R, ID> R queryWithLogicalExpire(String keyPrefix, ID id, Class<R> type,
                                            Function<ID, R> dbFallback, Long time) {
        //查询缓存
        String json = stringRedisTemplate.opsForValue().get(keyPrefix + id);
        //缓存为空
        if(StrUtil.isBlank(json)){
            return null;
        }
        //反序列化
        R r = null;
        RedisData redisData = JSONUtil.toBean(json, RedisData.class);
        LocalDateTime expireTime = redisData.getExpireTime();
        r = JSONUtil.toBean((JSONObject) redisData.getData(), type);
        //判断是否过期
        if(expireTime.isAfter(LocalDateTime.now())){
            //未过期
            return r;
        }
        //开启独立线程 进行缓存重建
        //为什么要获取锁？ 因为缓存重建要查询数据库，要防止多线程并发访问
        String lockKey = CACHE_SHOP_KEY + id;
        boolean isLock = tryLock(lockKey);
        // 判断是否获取锁成功
        if (isLock){
            CACHE_REBUILD_EXECUTOR.submit( ()->{
                try{
                    //重建缓存
                    R result = dbFallback.apply(id);
                    RedisData data = new RedisData();
                    data.setExpireTime(LocalDateTime.now().plusMinutes(time));
                    data.setData(result);
                    stringRedisTemplate.opsForValue().set(keyPrefix+id, JSONUtil.toJsonStr(data));
                }catch (Exception e){
                    throw new RuntimeException(e);
                }finally {
                    unlock(lockKey);
                }
            });
        }

        return r;
    }

    private Boolean tryLock(String key) {
        Boolean success = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", LOCK_SHOP_TTL, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(success);
    }

    private void unlock(String key){
        stringRedisTemplate.delete(key);
    }
}
