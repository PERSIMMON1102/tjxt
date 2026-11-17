package com.tianji.learning.service;

import com.tianji.learning.domain.vo.SignResultVO;

import java.util.List;

public interface ISignRecordService {

    /**
     * 签到
     *
     * @return 签到结果
     */
    SignResultVO addSignRecords();

    /**
     * 查询本月第一天到今天为止每一天的签到情况
     *
     * @return 签到记录，0-未签到，1-已签到，按下标对应本月的每一天
     */
    List<Integer> getSignRecords();
}