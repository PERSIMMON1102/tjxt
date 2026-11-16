package com.tianji.learning.domain.vo;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@ApiModel(description = "互动回答或评论信息")
public class ReplyVO {
    @ApiModelProperty("回答或评论id")
    private Long id;
    @ApiModelProperty("回答或评论内容")
    private String content;
    @ApiModelProperty("是否匿名")
    private Boolean anonymity;
    @ApiModelProperty("回答或评论者id")
    private Long userId;
    @ApiModelProperty("回答或评论者昵称")
    private String userName;
    @ApiModelProperty("回答或评论者头像")
    private String userIcon;
    @ApiModelProperty(value = "回答或评论时间", example = "2022-7-18 19:52:36")
    private LocalDateTime createTime;
    @ApiModelProperty("被评论的目标用户昵称")
    private String targetUserName;
    @ApiModelProperty("回答下的评论数量")
    private Integer replyTimes;
    @ApiModelProperty("点赞数量")
    private Integer likedTimes;
    @ApiModelProperty("当前用户是否点赞过")
    private Boolean liked;
    @ApiModelProperty("是否被隐藏，仅管理端使用")
    private Boolean hidden;
}