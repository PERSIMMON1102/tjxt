package com.tianji.exam.domain.vo;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.util.List;

/**
 * 考试中的题目信息，不包含正确答案
 *
 * @author Tom
 */
@Data
@ApiModel(description = "考试题目信息")
public class ExamQuestionVO {

    @ApiModelProperty("题目id")
    private Long id;

    @ApiModelProperty("题目名称")
    private String name;

    @ApiModelProperty("题目类型：1-单选题，2-多选题，3-不定向选择题，4-判断题，5-主观题")
    private Integer type;

    @ApiModelProperty("难易度：1-简单，2-中等，3-困难")
    private Integer difficulty;

    @ApiModelProperty("本题分值")
    private Integer score;

    @ApiModelProperty("选择题的选项")
    private List<String> options;
}