package com.whutassistant.controller;


import cn.hutool.core.collection.CollUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.whutassistant.dto.Result;
import com.whutassistant.entity.Shop;
import com.whutassistant.entity.ShopType;
import com.whutassistant.service.IShopTypeService;
import com.whutassistant.utils.SystemConstants;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static com.whutassistant.utils.RedisConstants.CACHE_SHOP_TYPE_KEY;
import static com.whutassistant.utils.RedisConstants.CACHE_SHOP_TYPE_TTL;

/**
 * <p>
 * 前端控制器
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@RestController
@RequestMapping("/shop-type")
public class ShopTypeController {
    @Resource
    private IShopTypeService typeService;


    @GetMapping("/list")
    public Result queryTypeList() {
        return typeService.queryTypeList();
    }
}

