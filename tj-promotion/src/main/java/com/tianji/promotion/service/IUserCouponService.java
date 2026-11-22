package com.tianji.promotion.service;

import com.tianji.common.domain.dto.PageDTO;
import com.tianji.promotion.domain.dto.UserCouponDTO;
import com.tianji.promotion.domain.po.UserCoupon;
import com.baomidou.mybatisplus.extension.service.IService;
import com.tianji.promotion.domain.query.UserCouponQuery;
import com.tianji.promotion.domain.vo.UserCouponVO;

import java.util.List;

/**
 * <p>
 * 用户领取优惠券的记录，是真正使用的优惠券信息 服务类
 * </p>
 *
 * @author Tom
 * @since 2026-11-18
 */
public interface IUserCouponService extends IService<UserCoupon> {

    void receiveCoupon(Long couponId);

    void exchangeCoupon(String code);

    void checkAndCreateUserCoupon(UserCouponDTO uc);

    PageDTO<UserCouponVO> queryMyCoupons(UserCouponQuery query);

    /**
     * 给即将过期的优惠券发送提醒短信
     *
     * @param shardIndex 当前分片序号
     * @param shardTotal 分片总数
     * @return 本次提醒的优惠券数量
     */
    int remindExpiringCoupons(int shardIndex, int shardTotal);

    void writeOffCoupon(List<Long> userCouponIds);

    void refundCoupon(List<Long> userCouponIds);

    List<String> queryDiscountRules(List<Long> userCouponIds);
}