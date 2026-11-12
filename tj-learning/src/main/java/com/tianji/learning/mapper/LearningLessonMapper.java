package com.tianji.learning.mapper;

import com.tianji.learning.domain.po.LearningLesson;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;

/**
 * <p>
 * 学生课程表 Mapper 接口
 * </p>
 *
 * @author Tom
 * @since 2026-11-01
 */
public interface LearningLessonMapper extends BaseMapper<LearningLesson> {

    /**
     * 统计用户本周计划学习的小节数量
     * @param userId 用户id
     * @return 本周计划学习的小节总数
     */
    Integer queryTotalPlan(@Param("userId") Long userId);
}
