package com.tianji.remark.task;

import com.tianji.remark.config.LikeProperties;
import com.tianji.remark.service.ILikedRecordService;
import com.xxl.job.core.handler.annotation.XxlJob;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 基于XXL-JOB的点赞数变更检测任务。
 * 在XXL-JOB管理端新建任务，JobHandler填写 likedTimesCheckHandler，Cron建议 0/20 * * * * ?。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LikedTimesJobHandler {

    private final ILikedRecordService recordService;
    private final LikeProperties likeProperties;

    @XxlJob("likedTimesCheckHandler")
    public void checkLikedTimes() {
        log.debug("xxl-job触发点赞数变更检测");
        for (String bizType : likeProperties.getBizTypes()) {
            // 1.推送点赞数变更
            recordService.readLikedTimesAndSendMessage(bizType, likeProperties.getMaxBizSize());
            // 2.持久化点赞记录
            recordService.flushLikedRecords(bizType, likeProperties.getMaxBizSize());
        }
    }
}