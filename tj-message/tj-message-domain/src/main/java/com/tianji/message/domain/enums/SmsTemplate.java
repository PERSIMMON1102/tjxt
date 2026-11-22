package com.tianji.message.domain.enums;

import lombok.Getter;

@Getter
public enum SmsTemplate {
    VERIFY_CODE("短信验证码"),
    COUPON_EXPIRE_REMIND("优惠券过期提醒"),
    ;
    private String desc;

    SmsTemplate( String desc) {
        this.desc = desc;
    }
}
