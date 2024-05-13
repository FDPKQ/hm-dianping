package com.hmdp;

import ch.qos.logback.classic.spi.EventArgUtil;
import com.hmdp.service.impl.ShopServiceImpl;
import com.hmdp.utils.RedisIdWorker;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import javax.annotation.Resource;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;


@SpringBootTest
class HmDianPingApplicationTests {
    @Resource
    private ShopServiceImpl shopService;

    @Resource
    RedisIdWorker redisIdWorker;

    @Test
    void testSaveShop2Redis() throws InterruptedException {
        shopService.saveShop2Redis(2L, 10L);
    }

    private final ExecutorService es = Executors.newFixedThreadPool(10);

    @Test
    void testIdWork() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(300);

        Runnable task = () -> {
            for (int i = 0; i < 100; i++) {
                long order = redisIdWorker.nextId("order");
                System.out.println("order = " + order);
            }
            latch.countDown();
        };

        long begin = System.currentTimeMillis();
        for (int i = 0; i < 3000; i++) {
            es.submit(task);
        }
        latch.await();
        System.out.println("time : " + (System.currentTimeMillis() - begin));
    }

}
