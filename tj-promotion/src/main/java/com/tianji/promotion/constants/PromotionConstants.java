package com.tianji.promotion.constants;

public interface PromotionConstants {
    String COUPON_CODE_SERIAL_KEY="coupon:code:serial";
    /** 优惠券兑换码范围缓存，member：优惠券id，score：兑换码最大序列号 */
    String COUPON_RANGE_KEY = "coupon:code:range";
    String COUPON_CODE_MAP_KEY="coupon:code:map";
    /** 优惠券过期提醒标记，key = 前缀 + 用户券id，保证同一张券只提醒一次 */
    String COUPON_REMIND_KEY = "coupon:remind:";
    /** 优惠券缓存KEY前缀，完整KEY = 前缀 + 优惠券id */
    String COUPON_CACHE_KEY_PREFIX = "prs:coupon:";
    /** 用户已领优惠券数量缓存KEY前缀，完整KEY = 前缀 + 优惠券id，field = 用户id */
    String USER_COUPON_CACHE_KEY_PREFIX = "prs:user:coupon:";

    /**
     * 领券LUA脚本返回值对应的错误信息，下标 = 返回值 - 1
     * 0：成功；1：优惠券不存在；2：库存不足；3：不在发放时间内；4：超出限领；5：兑换码券
     */
    String[] RECEIVE_COUPON_ERROR_MSG = {
            "优惠券不存在",
            "优惠券库存不足",
            "优惠券发放已经结束或尚未开始",
            "超出领取数量",
            "该优惠券只能通过兑换码兑换"
    };

    /**
     * 兑换码LUA脚本返回值对应的错误信息，下标 = 返回值 - 1
     * 返回值 1~5 为错误码，成功时返回优惠券id
     */
    String[] EXCHANGE_COUPON_ERROR_MSG = {
            "兑换码已经被兑换过了",
            "兑换码不存在",
            "优惠券不存在",
            "优惠券发放已经结束或尚未开始",
            "超出领取数量"
    };
}