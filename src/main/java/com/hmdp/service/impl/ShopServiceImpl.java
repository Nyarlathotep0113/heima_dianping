package com.hmdp.service.impl;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hmdp.entity.RedisHotData;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisConstants;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);

    @Override
    public Shop queryById(Long id) {
        //Shop shop = queryWithPassThrough(id); 缓存穿透
        //互斥锁解决缓存击穿
        //Shop shop = queryWithMutex(id);
        Shop shop = queryWithLogicalExpire(id);
        return shop;
    }
    private Shop queryWithMutex(Long id){
        //1.从redis查询缓存
        String cacheShopJson = stringRedisTemplate.opsForValue().get(RedisConstants.CACHE_SHOP_KEY + id);
        //2.判断存在
        if (StrUtil.isNotBlank(cacheShopJson)) {
            Shop shop = JSONUtil.toBean(cacheShopJson, Shop.class);
            return shop;
        }
        if(cacheShopJson != null){
            return null;
        }
        String lockKey = RedisConstants.LOCK_SHOP_KEY + id;
        boolean lock = tryLock(lockKey);
        Shop shop = null;
        try {
            if(!lock){
                Thread.sleep(100);
                return queryWithMutex(id);
            }
            //获得锁之后再次检查 Redis 的原因主要是为了避免在获取锁的过程中，其他线程可能已经重建了缓存，导致当前线程重复执行不必要的操作
            cacheShopJson = stringRedisTemplate.opsForValue().get(RedisConstants.CACHE_SHOP_KEY + id);
            if (StrUtil.isNotBlank(cacheShopJson)) {
                shop = JSONUtil.toBean(cacheShopJson, Shop.class);
                return shop;
            }
            if(cacheShopJson != null){
                return null;
            }
            shop = getById(id);
            if (shop != null) {
                //5.将数据写入redis
                stringRedisTemplate.opsForValue()
                        .set(RedisConstants.CACHE_SHOP_KEY + id, JSONUtil.toJsonStr(shop),RedisConstants.CACHE_SHOP_TTL, TimeUnit.MINUTES);
                return shop;
            }
            stringRedisTemplate.opsForValue()
                    .set(RedisConstants.CACHE_SHOP_KEY + id, "", RedisConstants.CACHE_NULL_TTL, TimeUnit.MINUTES);
            return null;
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            unlock(lockKey);
        }
    }
    public void saveShop2Redis(Long id){
        //1.查询店铺数据
        Shop shop=getById(id);
        //2.封装逻辑过期时间
        RedisHotData<Shop> redisHotData=new RedisHotData<>();
        redisHotData.setData(shop);
        redisHotData.setExpireTime(LocalDateTime.now().plusMinutes(RedisConstants.CACHE_SHOP_TTL));
        //3.写入Redis
        stringRedisTemplate.opsForValue().set(RedisConstants.CACHE_SHOP_KEY + id, JSONUtil.toJsonStr(redisHotData));
    }
    private Shop queryWithLogicalExpire(Long id){
        //1.从redis查询缓存
        String cacheShopJson = stringRedisTemplate.opsForValue().get(RedisConstants.CACHE_SHOP_KEY + id);
        //2.判断存在
        if (StrUtil.isBlank(cacheShopJson)) {
            return null;
        }
        RedisHotData redisHotData = JSONUtil.toBean(cacheShopJson, RedisHotData.class);
        JSONObject jsonObject= (JSONObject) redisHotData.getData();
        Shop shop=jsonObject.toBean(Shop.class);
        //3.判断是否过期
        if (redisHotData.getExpireTime().isAfter(LocalDateTime.now())) {
            return shop;
        }
        String lockKey = RedisConstants.LOCK_SHOP_KEY + id;
        boolean lock = tryLock(lockKey);
        if(lock){
            try {
                //获得锁之后再次检查 Redis 的原因主要是为了避免在获取锁的过程中，其他线程可能已经重建了缓存，导致当前线程重复执行不必要的操作
                cacheShopJson = stringRedisTemplate.opsForValue().get(RedisConstants.CACHE_SHOP_KEY + id);
                if (StrUtil.isNotBlank(cacheShopJson)) {
                    redisHotData = JSONUtil.toBean(cacheShopJson, RedisHotData.class);
                    shop = jsonObject.toBean(Shop.class);
                    return shop;
                }else{
                    CACHE_REBUILD_EXECUTOR.submit(() -> saveShop2Redis(id));
                }
            } finally {
                unlock(lockKey);
            }
        }
        return shop;
    }
    private Shop queryWithPassThrough(Long id){
        //1.从redis查询缓存
        String cacheShopJson = stringRedisTemplate.opsForValue().get(RedisConstants.CACHE_SHOP_KEY + id);
        //2.判断存在
        if (StrUtil.isNotBlank(cacheShopJson)) {
            Shop shop = JSONUtil.toBean(cacheShopJson, Shop.class);
            return shop;
        }
        if(cacheShopJson != null){
            return null;
        }
        //4.不存在，根据id查询数据库
        Shop shop = getById(id);
        if (shop != null) {
            //5.将数据写入redis
            stringRedisTemplate.opsForValue()
                    .set(RedisConstants.CACHE_SHOP_KEY + id, JSONUtil.toJsonStr(shop),RedisConstants.CACHE_SHOP_TTL, TimeUnit.MINUTES);
            return shop;
        }
        //6.不存在，返回null，并将空值写入redis，实现缓存穿透保护
        stringRedisTemplate.opsForValue()
                .set(RedisConstants.CACHE_SHOP_KEY + id, "", RedisConstants.CACHE_NULL_TTL, TimeUnit.MINUTES);
        return null;
    }
    private boolean tryLock(String key){
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", 10, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(flag);
    }
    private void unlock(String key){
        stringRedisTemplate.delete(key);
    }

    @Override
    @Transactional
    public boolean update(Shop shop) {
        Long id=shop.getId();
        if(id==null){
            return false;
        }
        //1.更新数据库
        updateById(shop);
        //2.删除缓存
        stringRedisTemplate.delete(RedisConstants.CACHE_SHOP_KEY + shop.getId());
        return true;
    }
}
