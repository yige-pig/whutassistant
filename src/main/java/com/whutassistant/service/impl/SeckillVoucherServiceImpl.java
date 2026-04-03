package com.whutassistant.service.impl;

import com.whutassistant.dto.Result;
import com.whutassistant.entity.SeckillVoucher;
import com.whutassistant.entity.VoucherOrder;
import com.whutassistant.mapper.SeckillVoucherMapper;
import com.whutassistant.service.ISeckillVoucherService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.whutassistant.service.IVoucherOrderService;
import com.whutassistant.utils.RedisIdWorker;
import com.whutassistant.utils.UserHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;

/**
 * <p>
 * 秒杀优惠券表，与优惠券是一对一关系 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2022-01-04
 */
@Service
public class SeckillVoucherServiceImpl extends ServiceImpl<SeckillVoucherMapper, SeckillVoucher> implements ISeckillVoucherService {


}

