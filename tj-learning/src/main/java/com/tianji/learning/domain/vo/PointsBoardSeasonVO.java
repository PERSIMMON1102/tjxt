package com.tianji.learning.domain.vo;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.time.LocalDate;

@Data
@ApiModel(description = "赛季信息")
public class PointsBoardSeasonVO {

    @ApiModelProperty("赛季id")
    private Long id;

    @ApiModelProperty("赛季名称，例如：第一赛季")
    private String name;

    @ApiModelProperty("赛季开始时间")
    private LocalDate beginTime;

    @ApiModelProperty("赛季结束时间")
    private LocalDate endTime;
}