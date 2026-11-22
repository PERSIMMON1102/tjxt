package com.tianji.promotion.domain.vo;

import com.tianji.promotion.enums.CouponStatus;
import com.tianji.promotion.enums.DiscountType;
import com.tianji.promotion.enums.ObtainType;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 管理端优惠券详情，用于优惠券详情页展示、修改表单回显
 */
@Data
@ApiModel(description = "管理端优惠券详情")
public class CouponDetailVO {

    @ApiModelProperty("优惠券id")
    private Long id;
    @ApiModelProperty("优惠券名称")
    private String name;
    @ApiModelProperty("是否限定使用范围")
    private Boolean specific;
    @ApiModelProperty("限定的使用范围")
    private List<CouponScopeVO> scopes;

    @ApiModelProperty("优惠券折扣类型，1：每满减，2：折扣，3：无门槛，4：普通满减")
    private DiscountType discountType;
    @ApiModelProperty("折扣门槛，0代表无门槛")
    private Integer thresholdAmount;
    @ApiModelProperty("折扣值，满减填抵扣金额；打折填折扣值：80标示打8折")
    private Integer discountValue;
    @ApiModelProperty("最大优惠金额")
    private Integer maxDiscountAmount;

    @ApiModelProperty("获取方式1：手动领取，2：指定发放（通过兑换码兑换）")
    private ObtainType obtainWay;
    @ApiModelProperty("优惠券总量")
    private Integer totalNum;
    @ApiModelProperty("已发放数量")
    private Integer issueNum;
    @ApiModelProperty("已使用数量")
    private Integer usedNum;
    @ApiModelProperty("每人限领数量")
    private Integer userLimit;

    @ApiModelProperty("发放开始时间")
    private LocalDateTime issueBeginTime;
    @ApiModelProperty("发放结束时间")
    private LocalDateTime issueEndTime;
    @ApiModelProperty("有效天数")
    private Integer termDays;
    @ApiModelProperty("使用有效期开始时间")
    private LocalDateTime termBeginTime;
    @ApiModelProperty("使用有效期结束时间")
    private LocalDateTime termEndTime;

    @ApiModelProperty("优惠券状态，1：待发放，2：未开始，3：发放中，4：发放结束，5：暂停")
    private CouponStatus status;
}