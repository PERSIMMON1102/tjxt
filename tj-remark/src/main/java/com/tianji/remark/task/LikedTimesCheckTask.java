package com.tianji.remark.task;

import com.tianji.remark.config.LikeProperties;
import com.tianji.remark.service.ILikedRecordService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 基于SpringTask的点赞数变更检测任务。
 * <p>
 * 与XXL-JOB版本（{@link LikedTimesJobHandler}）二选一：
 * 在Nacos的remark-service.yaml中把 tj.like.spring-task-enabled 改为 false，
 * 并在XXL-JOB管理端创建 likedTimesCheckHandler 任务后，本任务就不会再执行，避免重复处理。
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "tj.like", name = "spring-task-enabled", havingValue = "true", matchIfMissing = true)
public class LikedTimesCheckTask {

    private final ILikedRecordService recordService;
    private final LikeProperties likeProperties;

    @Scheduled(fixedDelay = 20000)
    public void checkLikedTimes() {
        for (String bizType : likeProperties.getBizTypes()) {
            // 1.推送点赞数变更
            recordService.readLikedTimesAndSendMessage(bizType, likeProperties.getMaxBizSize());
            // 2.持久化点赞记录
            recordService.flushLikedRecords(bizType, likeProperties.getMaxBizSize());
        }
    }
}