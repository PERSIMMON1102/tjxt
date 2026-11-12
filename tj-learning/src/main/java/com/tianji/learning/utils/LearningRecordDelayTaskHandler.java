package com.tianji.learning.utils;

import com.tianji.common.utils.CollUtils;
import com.tianji.common.utils.JsonUtils;
import com.tianji.common.utils.StringUtils;
import com.tianji.learning.domain.po.LearningLesson;
import com.tianji.learning.domain.po.LearningRecord;
import com.tianji.learning.mapper.LearningRecordMapper;
import com.tianji.learning.service.ILearningLessonService;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.concurrent.CustomizableThreadFactory;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.DelayQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class LearningRecordDelayTaskHandler {

    private final StringRedisTemplate redisTemplate;
    private final LearningRecordMapper recordMapper;
    private final ILearningLessonService lessonService;
    private final DelayQueue<DelayTask<RecordTaskData>> queue = new DelayQueue<>();
    private static final String RECORD_KEY_TEMPLATE = "learning:record:{}";
    private static final String RECORD_KEY_PREFIX = "learning:record:";
    private static final String STRATEGY_SCHEDULE = "schedule";
    private static volatile boolean begin = true;

    /**
     * 学习记录持久化策略：
     * delay：延迟队列 + 线程池（默认）
     * schedule：Spring 定时任务
     */
    @Value("${tj.learning.record.persist-strategy:delay}")
    private String persistStrategy;

    private ThreadPoolExecutor executor;

    @PostConstruct
    public void init() {
        if (!isDelayStrategy()) {
            log.info("学习记录持久化策略为 {}，不启动延迟任务线程池", persistStrategy);
            return;
        }
        // 1.核心线程数与CPU核数一致
        int corePoolSize = Runtime.getRuntime().availableProcessors();
        executor = new ThreadPoolExecutor(
                corePoolSize, corePoolSize,
                0L, TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<>(),
                new CustomizableThreadFactory("learning-record-delay-"));
        // 2.每个核心线程都作为一个延迟任务消费者，实现并发持久化
        for (int i = 0; i < corePoolSize; i++) {
            executor.execute(this::handleDelayTask);
        }
        log.info("学习记录延迟任务线程池启动，核心线程数：{}", corePoolSize);
    }

    @PreDestroy
    public void destroy() {
        begin = false;
        if (executor != null) {
            executor.shutdownNow();
        }
        log.debug("延迟任务停止执行！");
    }

    public void handleDelayTask() {
        while (begin) {
            try {
                // 1.获取到期的延迟任务
                DelayTask<RecordTaskData> task = queue.take();
                RecordTaskData data = task.getData();
                // 2.查询Redis缓存
                LearningRecord record = readRecordCache(data.getLessonId(), data.getSectionId());
                if (record == null) {
                    continue;
                }
                // 3.比较数据，moment值
                if (!Objects.equals(data.getMoment(), record.getMoment())) {
                    // 不一致，说明用户还在持续提交播放进度，放弃旧数据
                    continue;
                }
                // 4.一致，持久化播放进度数据到数据库
                persistRecord(data.getLessonId(), data.getSectionId(), record.getId(), record.getMoment());
            } catch (InterruptedException e) {
                // 线程池关闭时会中断阻塞中的线程
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                log.error("处理延迟任务发生异常", e);
            }
        }
    }

    public void addLearningRecordTask(LearningRecord record) {
        // 1.添加数据到Redis缓存（两种持久化策略共用）
        writeRecordCache(record);
        // 2.延迟任务模式才需要提交延迟任务到延迟队列
        if (isDelayStrategy()) {
            queue.add(new DelayTask<>(new RecordTaskData(record), Duration.ofSeconds(20)));
        }
    }

    /**
     * 定时任务方案：扫描Redis缓存，将学习记录持久化到数据库
     * @return 持久化的记录条数
     */
    public int persistCacheRecords() {
        // 1.获取所有学习记录缓存key
        Set<String> keys = redisTemplate.keys(RECORD_KEY_PREFIX + "*");
        if (CollUtils.isEmpty(keys)) {
            return 0;
        }
        int count = 0;
        // 2.遍历每一个课表的缓存
        for (String key : keys) {
            Long lessonId = parseId(key.substring(RECORD_KEY_PREFIX.length()));
            if (lessonId == null) {
                continue;
            }
            Map<Object, Object> entries = redisTemplate.opsForHash().entries(key);
            if (CollUtils.isEmpty(entries)) {
                continue;
            }
            // 3.遍历课表下每一个小节的学习记录
            for (Map.Entry<Object, Object> entry : entries.entrySet()) {
                Long sectionId = parseId(entry.getKey().toString());
                RecordCacheData data = JsonUtils.toBean(entry.getValue().toString(), RecordCacheData.class);
                if (sectionId == null || data == null || data.getId() == null) {
                    continue;
                }
                // 4.持久化
                persistRecord(lessonId, sectionId, data.getId(), data.getMoment());
                count++;
            }
        }
        return count;
    }

    /**
     * 持久化单个学习记录：更新播放进度及课表最近学习信息
     */
    private void persistRecord(Long lessonId, Long sectionId, Long recordId, Integer moment) {
        // 1.更新学习记录的播放进度（只更新播放进度，避免覆盖完成状态等字段）
        LearningRecord record = new LearningRecord();
        record.setId(recordId);
        record.setMoment(moment);
        recordMapper.updateById(record);
        // 2.更新课表最近学习信息
        LearningLesson lesson = new LearningLesson();
        lesson.setId(lessonId);
        lesson.setLatestSectionId(sectionId);
        lesson.setLatestLearnTime(LocalDateTime.now());
        lessonService.updateById(lesson);
    }

    private boolean isDelayStrategy() {
        return !STRATEGY_SCHEDULE.equals(persistStrategy);
    }

    private Long parseId(String value) {
        try {
            return Long.valueOf(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public void writeRecordCache(LearningRecord record) {
        log.debug("更新学习记录的缓存数据");
        try {
            // 1.数据转换
            String json = JsonUtils.toJsonStr(new RecordCacheData(record));
            // 2.写入Redis
            String key = StringUtils.format(RECORD_KEY_TEMPLATE, record.getLessonId());
            redisTemplate.opsForHash().put(key, record.getSectionId().toString(), json);
            // 3.添加缓存过期时间
            redisTemplate.expire(key, Duration.ofMinutes(1));
        } catch (Exception e) {
            log.error("更新学习记录缓存异常", e);
        }
    }

    public LearningRecord readRecordCache(Long lessonId, Long sectionId) {
        try {
            // 1.读取Redis数据
            String key = StringUtils.format(RECORD_KEY_TEMPLATE, lessonId);
            Object cacheData = redisTemplate.opsForHash().get(key, sectionId.toString());
            if (cacheData == null) {
                return null;
            }
            // 2.数据检查和转换
            return JsonUtils.toBean(cacheData.toString(), LearningRecord.class);
        } catch (Exception e) {
            log.error("缓存读取异常", e);
            return null;
        }
    }

    public void cleanRecordCache(Long lessonId, Long sectionId) {
        // 删除数据
        String key = StringUtils.format(RECORD_KEY_TEMPLATE, lessonId);
        redisTemplate.opsForHash().delete(key, sectionId.toString());
    }

    @Data
    @NoArgsConstructor
    private static class RecordCacheData {
        private Long id;
        private Integer moment;
        private Boolean finished;

        public RecordCacheData(LearningRecord record) {
            this.id = record.getId();
            this.moment = record.getMoment();
            this.finished = record.getFinished();
        }
    }

    @Data
    @NoArgsConstructor
    private static class RecordTaskData {
        private Long lessonId;
        private Long sectionId;
        private Integer moment;

        public RecordTaskData(LearningRecord record) {
            this.lessonId = record.getLessonId();
            this.sectionId = record.getSectionId();
            this.moment = record.getMoment();
        }
    }
}