package com.whutassistant.service;

import com.whutassistant.dto.Result;
import com.whutassistant.entity.Shop;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
public interface IShopService extends IService<Shop> {

    Result queryShopById(Long id);

    void update(Shop shop);

    void saveShopToCache(Long id, Long expireSeconds);

    Result queryShopByType(Integer typeId, Integer current, Double x, Double y);
}

