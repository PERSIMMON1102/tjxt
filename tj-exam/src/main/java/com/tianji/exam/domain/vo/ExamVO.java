package com.tianji.exam.domain.vo;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.util.List;

/**
 * 开始考试后返回的考试信息
 *
 * @author Tom
 */
@Data
@ApiModel(description = "考试信息及题目列表")
public class ExamVO {

    @ApiModelProperty("考试记录id")
    private String id;

    @ApiModelProperty("考试题目列表")
    private List<ExamQuestionVO> questions;
}