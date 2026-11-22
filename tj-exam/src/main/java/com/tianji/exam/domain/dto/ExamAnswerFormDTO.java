package com.tianji.exam.domain.dto;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import javax.validation.constraints.NotNull;

/**
 * 交卷时每道题的作答信息
 *
 * @author Tom
 */
@Data
@ApiModel(description = "考试答题信息")
public class ExamAnswerFormDTO {

    @ApiModelProperty(value = "题目id", example = "12351412312141")
    @NotNull(message = "题目id不能为空")
    private Long questionId;

    @ApiModelProperty(value = "题目类型：1-单选题，2-多选题，3-不定向选择题，4-判断题，5-主观题", example = "1")
    @NotNull(message = "题目类型不能为空")
    private Integer questionType;

    @ApiModelProperty(value = "题目答案", example = "1,2,3")
    private String answer;
}