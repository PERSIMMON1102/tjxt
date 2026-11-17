package com.tianji.learning.mapper;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.Constants;
import com.tianji.learning.domain.po.PointsRecord;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

/**
 * <p>
 * 学习积分记录，每个月底清零 Mapper 接口
 * </p>
 */
public interface PointsRecordMapper extends BaseMapper<PointsRecord> {

    @Select("SELECT SUM(points) FROM points_record ${ew.customSqlSegment}")
    Integer queryUserPointsByTypeAndDate(@Param(Constants.WRAPPER) QueryWrapper<PointsRecord> wrapper);

    @Select("SELECT type, SUM(points) AS points FROM points_record ${ew.customSqlSegment} GROUP BY type")
    List<PointsRecord> queryUserPointsByDate(@Param(Constants.WRAPPER) QueryWrapper<PointsRecord> wrapper);

    /**
     * 创建积分明细历史表
     *
     * @param tableName 表名，例如 points_record_10
     */
    void createPointsRecordTable(@Param("tableName") String tableName);

    /**
     * 把指定时间范围内的积分明细迁移到历史表
     *
     * @param tableName 历史表名
     * @param begin     开始时间（含）
     * @param end       结束时间（不含）
     * @return 迁移条数
     */
    int migratePointsRecord(
            @Param("tableName") String tableName,
            @Param("begin") LocalDateTime begin,
            @Param("end") LocalDateTime end);

    /**
     * 删除已经迁移的积分明细
     *
     * @param begin 开始时间（含）
     * @param end   结束时间（不含）
     * @return 删除条数
     */
    int deletePointsRecord(@Param("begin") LocalDateTime begin, @Param("end") LocalDateTime end);
}