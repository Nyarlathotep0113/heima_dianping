package com.hmdp.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisConstants;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
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

    @Override
    public Shop queryById(Long id) {
        //1.从redis查询缓存
        String cacheShopJson = stringRedisTemplate.opsForValue().get(RedisConstants.CACHE_SHOP_KEY + id);
        //2.判断存在
        if (StrUtil.isNotBlank(cacheShopJson)) {
            Shop shop = JSONUtil.toBean(cacheShopJson, Shop.class);
            return shop;
        }
        //4.不存在，根据id查询数据库
        Shop shop = getById(id);
        if (shop != null) {
            //5.将数据写入redis
            stringRedisTemplate.opsForValue()
                    .set(RedisConstants.CACHE_SHOP_KEY + id, JSONUtil.toJsonStr(shop),RedisConstants.CACHE_SHOP_TTL, TimeUnit.MINUTES);
            return shop;
        }
        return null;
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
