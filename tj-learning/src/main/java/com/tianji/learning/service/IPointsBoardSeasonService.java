package com.tianji.learning.service;

import com.tianji.learning.domain.po.PointsBoardSeason;
import com.baomidou.mybatisplus.extension.service.IService;
import com.tianji.learning.domain.vo.PointsBoardSeasonVO;

import java.time.LocalDateTime;
import java.util.List;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author Tom
 * @since 2026-11-16
 */
public interface IPointsBoardSeasonService extends IService<PointsBoardSeason> {

    /**
     * 查询全部历史赛季
     *
     * @return 历史赛季列表
     */
    List<PointsBoardSeasonVO> querySeasonList();

    Integer querySeasonByTime(LocalDateTime time);
}