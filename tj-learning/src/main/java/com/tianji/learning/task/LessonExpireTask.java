package com.tianji.learning.task;

import com.tianji.learning.service.ILearningLessonService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 课程过期状态检查定时任务
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LessonExpireTask {

    private final ILearningLessonService lessonService;

    /**
     * 定期检查课表中的课程是否过期，已过期的课程状态更新为已失效。
     * 默认每天凌晨2点执行一次，可通过 tj.learning.lesson-expire-cron 配置修改。
     */
    @Scheduled(cron = "${tj.learning.lesson-expire-cron:0 0 2 * * ?}")
    public void checkLessonExpire() {
        int count = lessonService.updateExpiredLessons();
        if (count > 0) {
            log.info("课程过期检查完成，已将 {} 个过期课程标记为已失效", count);
        } else {
            log.debug("课程过期检查完成，没有需要处理的课程");
        }
    }
}