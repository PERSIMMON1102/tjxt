package com.tianji.promotion.handler;

import com.tianji.promotion.domain.dto.UserCouponDTO;
import com.tianji.promotion.service.IUserCouponService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.ExchangeTypes;
import org.springframework.amqp.rabbit.annotation.Exchange;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.QueueBinding;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import static com.tianji.common.constants.MqConstants.Exchange.PROMOTION_EXCHANGE;
import static com.tianji.common.constants.MqConstants.Key.COUPON_RECEIVE;

@Slf4j
@RequiredArgsConstructor
@Component
public class PromotionMqHandler {

    private final IUserCouponService userCouponService;

    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(name = "coupon.receive.queue", durable = "true"),
            exchange = @Exchange(name = PROMOTION_EXCHANGE, type = ExchangeTypes.TOPIC),
            key = COUPON_RECEIVE
    ))
    public void listenCouponReceiveMessage(UserCouponDTO uc) {
        // 1.健壮性处理
        if (uc == null || uc.getUserId() == null || uc.getCouponId() == null) {
            log.error("接收到的领券消息有误，消息内容：{}", uc);
            return;
        }
        // 2.更新优惠券发放数量并新增用户券
        log.debug("监听到用户{}领取优惠券{}的消息", uc.getUserId(), uc.getCouponId());
        userCouponService.checkAndCreateUserCoupon(uc);
    }
}