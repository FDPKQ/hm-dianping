package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.hmdp.entity.ShopType;
import com.hmdp.mapper.ShopTypeMapper;
import com.hmdp.service.IShopTypeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.SHOP_TYPE_LIST_KEY;
import static com.hmdp.utils.RedisConstants.SHOP_TYPE_LIST_KEY_TTL;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {
    @Resource
    StringRedisTemplate stringRedisTemplate;

    @Override
    public List<ShopType> queryAllOrderBySort() {
        String shopTypeListJsonArray = stringRedisTemplate.opsForValue().get(SHOP_TYPE_LIST_KEY);

        if (StrUtil.isNotBlank(shopTypeListJsonArray)) {
            return JSONUtil.toList(shopTypeListJsonArray, ShopType.class);
        }


        List<ShopType> shopTypeList = query().orderByAsc("sort").list();

        if (shopTypeList.isEmpty()) {
            return shopTypeList;
        }

        stringRedisTemplate.opsForValue().set(SHOP_TYPE_LIST_KEY, JSONUtil.toJsonStr(shopTypeList), SHOP_TYPE_LIST_KEY_TTL, TimeUnit.HOURS);
        return shopTypeList;
    }
}
