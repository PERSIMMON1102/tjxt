package com.tianji.exam.domain.vo;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

/**
 * 考试详情中每道题的作答及题目信息
 *
 * @author Tom
 */
@Data
@ApiModel(description = "考试详情")
public class ExamDetailVO {

    @ApiModelProperty("学员答案")
    private String answer;

    @ApiModelProperty("老师评语")
    private String comment;

    @ApiModelProperty("是否正确，主观题为null")
    private Boolean correct;

    @ApiModelProperty("学员得分")
    private Integer score;

    @ApiModelProperty("题目信息")
    private ExamQuestionDetailVO question;
}