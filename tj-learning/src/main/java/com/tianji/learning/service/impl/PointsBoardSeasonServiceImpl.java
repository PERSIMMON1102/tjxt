package com.tianji.learning.service.impl;

import com.tianji.common.utils.CollUtils;
import com.tianji.learning.domain.po.PointsBoardSeason;
import com.tianji.learning.domain.vo.PointsBoardSeasonVO;
import com.tianji.learning.mapper.PointsBoardSeasonMapper;
import com.tianji.learning.service.IPointsBoardSeasonService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author Tom
 * @since 2026-11-16
 */
@Service
public class PointsBoardSeasonServiceImpl extends ServiceImpl<PointsBoardSeasonMapper, PointsBoardSeason> implements IPointsBoardSeasonService {

    @Override
    public List<PointsBoardSeasonVO> querySeasonList() {
        // 1.查询已经开始的历史赛季（开始时间不晚于今天）
        List<PointsBoardSeason> seasons = lambdaQuery()
                .le(PointsBoardSeason::getBeginTime, LocalDate.now())
                .orderByAsc(PointsBoardSeason::getId)
                .list();
        if (CollUtils.isEmpty(seasons)) {
            return CollUtils.emptyList();
        }
        // 2.转换并返回
        List<PointsBoardSeasonVO> vos = new ArrayList<>(seasons.size());
        for (PointsBoardSeason season : seasons) {
            PointsBoardSeasonVO vo = new PointsBoardSeasonVO();
            vo.setId(season.getId() == null ? null : season.getId().longValue());
            vo.setName(season.getName());
            vo.setBeginTime(season.getBeginTime());
            vo.setEndTime(season.getEndTime());
            vos.add(vo);
        }
        return vos;
    }

    @Override
    public Integer querySeasonByTime(LocalDateTime time) {
        Optional<PointsBoardSeason> optional = lambdaQuery()
                .le(PointsBoardSeason::getBeginTime, time)
                .ge(PointsBoardSeason::getEndTime, time)
                .oneOpt();
        return optional.map(PointsBoardSeason::getId).orElse(null);
    }
}