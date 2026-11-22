package com.tianji.exam.domain.dto;

import com.tianji.common.validate.annotations.EnumValid;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import javax.validation.constraints.NotNull;

/**
 * 开始考试的表单数据
 *
 * @author Tom
 */
@Data
@ApiModel(description = "开始考试表单数据")
public class ExamFormDTO {

    @ApiModelProperty(value = "课程id", example = "1578558664933920770")
    @NotNull(message = "课程id不能为空")
    private Long courseId;

    @ApiModelProperty(value = "小节id", example = "12341")
    @NotNull(message = "小节id不能为空")
    private Long sectionId;

    @ApiModelProperty(value = "考试类型：1-练习，2-考试", example = "1")
    @NotNull(message = "考试类型不能为空")
    @EnumValid(enumeration = {1, 2}, message = "考试类型错误，只能是：1-练习，2-考试")
    private Integer type;
}