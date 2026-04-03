package com.hmdp.service.impl;

import com.hmdp.dto.Result;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.SimpleRedisLock;
import com.hmdp.utils.UserHolder;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.aop.framework.AopContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {

    @Resource
    private RedisIdWorker redisIdWorker;
    @Resource
    private ISeckillVoucherService seckillVoucherService;
    @Autowired
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private RedissonClient redissonClient;

    private static final DefaultRedisScript<Long> SECKILL_SCRIPT;
    static {
        SECKILL_SCRIPT = new DefaultRedisScript<>();
        SECKILL_SCRIPT.setLocation(new ClassPathResource("seckill.lua"));
        SECKILL_SCRIPT.setResultType(Long.class);
    }

    //线程池
    private static final ExecutorService SECKILL_ORDER_EXECUTOR = Executors.newSingleThreadExecutor();

    //阻塞队列
    private BlockingQueue<VoucherOrder> orderTasks = new ArrayBlockingQueue<>(1024*1024);

    //代理对象
    private IVoucherOrderService proxy;

    private class VoucherOrderHandler implements Runnable{
        @Override
        public void run() {
            while(true){
                try {
                    VoucherOrder voucherOrder = orderTasks.take();
                    handleVoucherOrder(voucherOrder);
                } catch (InterruptedException e) {
                    log.error("订单处理异常");
                }
            }
        }


    }

    //类初始化完后执行线程任务
    @PostConstruct
    private void init(){
        SECKILL_ORDER_EXECUTOR.submit(new VoucherOrderHandler());
    }

    private void handleVoucherOrder(VoucherOrder voucherOrder) {
        Long userId = voucherOrder.getUserId();
        RLock lock = redissonClient.getLock("lock:order:" + userId);
        boolean isLock = lock.tryLock();
        if(!isLock){
            return;
        }
        try {
            proxy.createVoucherOrder(voucherOrder);
        } finally {
            lock.unlock();
        }
    }

    /*@Override
    @Transactional
    public Result seckillVoucher(Long voucherId) {
        //查询优惠券
        SeckillVoucher seckillVoucher = seckillVoucherService.getById(voucherId);
        //判断时间是否未到
        if(LocalDateTime.now().isBefore(seckillVoucher.getBeginTime())){
            return Result.fail("秒杀未开始");
        }
        //判断时间是否已过
        if(LocalDateTime.now().isAfter(seckillVoucher.getEndTime())){
            return Result.fail("秒杀已结束");
        }
        //判断是否有库存
        if(seckillVoucher.getStock()!=null && seckillVoucher.getStock()<=0){
            return Result.fail("秒杀券被抢完");
        }
        //加分布式锁，创建订单
        //SimpleRedisLock lock = new SimpleRedisLock("order", stringRedisTemplate);
        RLock lock = redissonClient.getLock("lock:order:" + UserHolder.getUser().getId());
        //boolean isLock = lock.tryLock(1200L);
        boolean isLock = lock.tryLock();
        if(!isLock){
            return Result.fail("加锁失败");
        }
        try {
            //获取动态代理对象
            IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
            return proxy.createVoucherOrder(seckillVoucher);
        } finally {
            lock.unlock();
        }
    }*/
    @Override
    @Transactional
    public Result seckillVoucher(Long voucherId) {
        //执行lua脚本
        Long result = stringRedisTemplate.execute(
                SECKILL_SCRIPT,
                Collections.emptyList(),
                voucherId,
                UserHolder.getUser().getId()
        );
        int r = result.intValue();
        //判断结果是否为0
        if(r != 0){
            return Result.fail(r == 1?"库存不足":"不能重复下单");
        }
        //创建订单
        VoucherOrder voucherOrder = new VoucherOrder();
        voucherOrder.setUserId(UserHolder.getUser().getId());
        long orderId = redisIdWorker.nextId("order");
        voucherOrder.setId(orderId);
        voucherOrder.setVoucherId(voucherId);
        //放入阻塞队列
        orderTasks.add(voucherOrder);
        //获取代理对象
        proxy = (IVoucherOrderService) AopContext.currentProxy();
        return Result.ok(orderId);
    }

    /*@Transactional
    public Result createVoucherOrder(SeckillVoucher seckillVoucher){
        //判断该用户是否已下单
        Long count = lambdaQuery().eq(VoucherOrder::getVoucherId, seckillVoucher.getVoucherId())
                .eq(VoucherOrder::getUserId, UserHolder.getUser().getId())
                .count();
        if(count>=1){
            return Result.fail("用户已经购买过一次");
        }
        //减少库存 添加乐观锁
        boolean update = seckillVoucherService.lambdaUpdate()
                .eq(SeckillVoucher::getVoucherId, seckillVoucher.getVoucherId())
                .setSql("stock = stock - 1")
                .gt(SeckillVoucher::getStock,0).update();
        if(!update){
            throw new RuntimeException("秒杀券扣减失败");
        }
        //添加优惠券订单表
        VoucherOrder voucherOrder = new VoucherOrder();
        long orderId = redisIdWorker.nextId("seckill");
        voucherOrder.setId(orderId);
        voucherOrder.setVoucherId(seckillVoucher.getVoucherId());
        voucherOrder.setCreateTime(LocalDateTime.now());
        voucherOrder.setUpdateTime(LocalDateTime.now());
        voucherOrder.setUserId(UserHolder.getUser().getId());
        save(voucherOrder);

        return Result.ok(orderId);
    }*/
    @Transactional
    public void createVoucherOrder(VoucherOrder voucherOrder){
        Long userId = voucherOrder.getUserId();
        //判断用户是否下过单
        Long count = lambdaQuery().eq(VoucherOrder::getUserId, userId)
                .eq(VoucherOrder::getVoucherId, voucherOrder.getVoucherId())
                .count();
        if(count>0){
            return;
        }
        //减少库存
        boolean success = seckillVoucherService.update()
                .setSql("stock = stock - 1")
                .eq("voucher_id", voucherOrder.getVoucherId())
                .gt("stock", 0)
                .update();
        if(!success){
            return;
        }
        save(voucherOrder);
    }
}
