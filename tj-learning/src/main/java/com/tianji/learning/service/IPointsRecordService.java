package com.tianji.learning.service;

import com.tianji.learning.domain.po.PointsRecord;
import com.baomidou.mybatisplus.extension.service.IService;
import com.tianji.learning.domain.vo.PointsStatisticsVO;
import com.tianji.learning.enums.PointsRecordType;

import java.util.List;

/**
 * <p>
 * 学习积分记录，每个月底清零 服务类
 * </p>
 *
 * @author Tom
 * @since 2026-11-16
 */
public interface IPointsRecordService extends IService<PointsRecord> {

    void addPointsRecord(Long userId, int i, PointsRecordType pointsRecordType);

    List<PointsStatisticsVO> queryMyPointsToday();

    /**
     * 把指定赛季的积分明细迁移到历史表
     *
     * @param season 赛季id
     */
    void migratePointsRecord(Integer season);
}
