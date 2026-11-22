package com.tianji.exam.domain.po;

import lombok.Data;

import java.io.Serializable;

/**
 * 考试中每道题的作答及批阅信息，内嵌在考试记录文档中
 *
 * @author Tom
 */
@Data
public class ExamAnswer implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 题目id */
    private Long questionId;

    /** 题目类型：1-单选题，2-多选题，3-不定向选择题，4-判断题，5-主观题 */
    private Integer questionType;

    /** 学员答案 */
    private String answer;

    /** 是否答对，主观题为null */
    private Boolean correct;

    /** 本题得分 */
    private Integer score;

    /** 老师评语 */
    private String comment;
}