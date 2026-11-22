package com.tianji.promotion.task;

import com.tianji.promotion.service.IUserCouponService;
import com.xxl.job.core.context.XxlJobHelper;
import com.xxl.job.core.handler.annotation.XxlJob;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 优惠券过期提醒任务。
 * 在XXL-JOB管理端新建任务，JobHandler填写 couponExpireRemindJobHandler，
 * 建议每天执行一次（例如 0 0 10 * * ?），并开启分片广播，
 * 任务通过 MOD(id, 分片总数) = 分片序号 避免多个执行器重复处理。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CouponExpireRemindJobHandler {

    private final IUserCouponService userCouponService;

    @XxlJob("couponExpireRemindJobHandler")
    public void couponExpireRemindJobHandler() {
        // 1.获取分片参数
        int shardIndex = XxlJobHelper.getShardIndex();
        int shardTotal = XxlJobHelper.getShardTotal();
        // 2.提醒即将过期的优惠券
        int count = userCouponService.remindExpiringCoupons(shardIndex, shardTotal);

        String message = String.format("优惠券过期提醒完成，分片[%d/%d]，共提醒%d张优惠券",
                shardIndex, shardTotal, count);
        log.debug(message);
        XxlJobHelper.log(message);
    }
}