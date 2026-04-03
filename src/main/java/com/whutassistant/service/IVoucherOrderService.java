package com.whutassistant.service;

import com.whutassistant.dto.Result;
import com.whutassistant.entity.SeckillVoucher;
import com.whutassistant.entity.VoucherOrder;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
public interface IVoucherOrderService extends IService<VoucherOrder> {

    Result seckillVoucher(Long voucherId);

    //Result createVoucherOrder(SeckillVoucher seckillVoucher);

    void createVoucherOrder(VoucherOrder voucherOrder);
}

