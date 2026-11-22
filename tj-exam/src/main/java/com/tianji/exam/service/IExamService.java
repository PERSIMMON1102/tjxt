package com.tianji.exam.service;

import com.tianji.common.domain.dto.PageDTO;
import com.tianji.common.domain.query.PageQuery;
import com.tianji.exam.domain.dto.ExamCommitDTO;
import com.tianji.exam.domain.dto.ExamFormDTO;
import com.tianji.exam.domain.vo.ExamDetailVO;
import com.tianji.exam.domain.vo.ExamPageVO;
import com.tianji.exam.domain.vo.ExamVO;

import java.util.List;

/**
 * 考试相关业务接口
 *
 * @author Tom
 */
public interface IExamService {

    /**
     * 查询考试题目，并开始考试
     *
     * @param formDTO 课程、小节及考试类型
     * @return 考试记录id及题目列表
     */
    ExamVO startExam(ExamFormDTO formDTO);

    /**
     * 提交考试结果，自动批阅并保存
     *
     * @param commitDTO 考试记录id及答题信息
     */
    void commitExam(ExamCommitDTO commitDTO);

    /**
     * 分页查询当前用户的考试记录
     *
     * @param query 分页参数
     * @return 考试记录分页结果
     */
    PageDTO<ExamPageVO> queryExamPage(PageQuery query);

    /**
     * 查询考试详情，包含每道题的作答、得分及题目信息
     *
     * @param id 考试记录id
     * @return 考试详情列表
     */
    List<ExamDetailVO> queryExamDetail(String id);
}