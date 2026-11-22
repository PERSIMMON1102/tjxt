package com.tianji.promotion.task;

import com.tianji.promotion.service.ICouponService;
import com.xxl.job.core.context.XxlJobHelper;
import com.xxl.job.core.handler.annotation.XxlJob;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 优惠券发放状态变更任务。
 * 在XXL-JOB管理端新建任务，JobHandler填写 couponIssueJobHandler，建议每2分钟执行一次。
 * 任务支持分片，通过 MOD(id, 分片总数) = 分片序号 保证每张优惠券只被一个执行器处理。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CouponIssueJobHandler {

    private final ICouponService couponService;

    @XxlJob("couponIssueJobHandler")
    public void couponIssueJobHandler() {
        // 1.获取分片参数
        int shardIndex = XxlJobHelper.getShardIndex();
        int shardTotal = XxlJobHelper.getShardTotal();
        // 2.开始发放：未开始 -> 发放中
        int beginCount = couponService.updateIssueBegin(shardIndex, shardTotal);
        // 3.结束发放：发放中 -> 发放结束
        int endCount = couponService.updateIssueEnd(shardIndex, shardTotal);

        String message = String.format("优惠券发放状态变更完成，分片[%d/%d]，开始发放%d张，结束发放%d张",
                shardIndex, shardTotal, beginCount, endCount);
        log.debug(message);
        XxlJobHelper.log(message);
    }
}