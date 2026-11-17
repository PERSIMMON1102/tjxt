package com.tianji.learning.handler;

import com.tianji.learning.service.IPointsBoardSeasonService;
import com.tianji.learning.service.IPointsRecordService;
import com.xxl.job.core.handler.annotation.XxlJob;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 积分明细历史数据迁移任务
 */
@Component
@RequiredArgsConstructor
public class PointsRecordPersistentHandler {

    private final IPointsBoardSeasonService seasonService;

    private final IPointsRecordService pointsRecordService;

    /**
     * 每月初把上个月的积分明细迁移到 points_record_xx 表
     */
    @XxlJob("migratePointsRecordJob")
    public void migratePointsRecordOfLastSeason() {
        // 1.获取上月时间
        LocalDateTime time = LocalDateTime.now().minusMonths(1);
        // 2.查询赛季id
        Integer season = seasonService.querySeasonByTime(time);
        if (season == null) {
            // 赛季不存在，无需迁移
            return;
        }
        // 3.迁移积分明细
        pointsRecordService.migratePointsRecord(season);
    }
}