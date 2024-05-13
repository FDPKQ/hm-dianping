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
import org.springframework.aop.framework.AopContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {

    @Resource
    private ISeckillVoucherService seckillVoucherService;

    @Resource
    private RedisIdWorker redisIdWorker;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result seckillVoucher(Long voucherId) {
        SeckillVoucher seckillVoucher = seckillVoucherService.getById(voucherId);
        if (seckillVoucher.getBeginTime().isAfter(LocalDateTime.now())) {
            return Result.fail("seckill not begin");
        }

        if (seckillVoucher.getEndTime().isBefore(LocalDateTime.now())) {
            return Result.fail("seckill end");
        }

        if (seckillVoucher.getStock() < 1) {
            return Result.fail("stock is 0");
        }

        // 对用户加锁
//        synchronized (UserHolder.getUser().getId().toString().intern()) {
//            // 获取事务代理对象
//            IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
//            return proxy.creatVoucherOrder(voucherId);
//        }
        SimpleRedisLock lock = new SimpleRedisLock(stringRedisTemplate, "order:" + UserHolder.getUser().getId());
        boolean isLock = lock.tryLock(5);
        if (!isLock) {
            return Result.fail("one person one order, hack!");
        }

        try {
            IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
            return proxy.creatVoucherOrder(voucherId);
        } finally {
            lock.unLock();
        }


    }


    @Transactional
    @Override
    public Result creatVoucherOrder(Long voucherId) {
        Integer count = query().eq("user_id", UserHolder.getUser().getId())
                .eq("voucher_id", voucherId)
                .count();

        if (count > 0) {
            return Result.fail("one person one order");
        }

        boolean success = seckillVoucherService.update()
                .setSql("stock = stock - 1")
                .eq("voucher_id", voucherId)
                .gt("stock", 0)
                .update();

        if (!success) {
            return Result.fail("not success");
        }


        VoucherOrder voucherOrder = new VoucherOrder();

        long orderId = redisIdWorker.nextId("order");

        voucherOrder.setId(orderId);
        voucherOrder.setUserId(UserHolder.getUser().getId());
        voucherOrder.setVoucherId(voucherId);
        save(voucherOrder);

        return Result.ok(orderId);
    }
}
