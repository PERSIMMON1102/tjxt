package com.tianji.exam.domain.dto;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotEmpty;
import java.util.List;

/**
 * 提交考试结果的表单数据
 *
 * @author Tom
 */
@Data
@ApiModel(description = "提交考试结果表单数据")
public class ExamCommitDTO {

    @ApiModelProperty(value = "考试记录id", example = "12351412312141")
    @NotBlank(message = "考试记录id不能为空")
    private String id;

    @ApiModelProperty(value = "考试答题信息")
    @NotEmpty(message = "考试答案不能为空")
    @Valid
    private List<ExamAnswerFormDTO> examDetails;
}