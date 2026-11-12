package com.tianji.learning.task;

import com.tianji.learning.utils.LearningRecordDelayTaskHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 学习记录持久化定时任务（定时任务方案）。
 * <p>
 * 通过 tj.learning.record.persist-strategy 切换持久化策略：
 * delay：基于延迟队列 + 线程池（默认）
 * schedule：基于本定时任务，定期扫描Redis缓存并持久化
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "tj.learning.record", name = "persist-strategy", havingValue = "schedule")
public class LearningRecordPersistTask {

    private final LearningRecordDelayTaskHandler taskHandler;

    @Scheduled(fixedDelay = 20000, initialDelay = 20000)
    public void persistLearningRecords() {
        int count = taskHandler.persistCacheRecords();
        if (count > 0) {
            log.info("定时任务持久化学习记录完成，共处理 {} 条缓存记录", count);
        } else {
            log.debug("定时任务持久化学习记录完成，没有需要处理的缓存记录");
        }
    }
}