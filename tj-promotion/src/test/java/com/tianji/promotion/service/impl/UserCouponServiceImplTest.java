package com.tianji.promotion.service.impl;

import com.tianji.common.autoconfigure.mq.RabbitMqHelper;
import com.tianji.common.exceptions.BizIllegalException;
import com.tianji.common.utils.UserContext;
import com.tianji.promotion.domain.dto.UserCouponDTO;
import com.tianji.promotion.domain.po.UserCoupon;
import com.tianji.promotion.enums.UserCouponStatus;
import com.tianji.promotion.mapper.CouponMapper;
import com.tianji.promotion.utils.CodeUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UserCouponServiceImplTest {

    private static final long COUPON_ID = 1234567890123456789L;
    private static final long SERIAL_NUM = 100L;

    private final StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);

    @SuppressWarnings("unchecked")
    private final HashOperations<String, Object, Object> hashOperations = mock(HashOperations.class);

    private final RabbitMqHelper mqHelper = mock(RabbitMqHelper.class);

    private final CouponMapper couponMapper = mock(CouponMapper.class);

    private final UserCouponServiceImpl userCouponService = spy(
            new UserCouponServiceImpl(couponMapper, null, redisTemplate, null, null, mqHelper));

    @AfterEach
    void tearDown() {
        UserContext.removeUser();
    }

    @Test
    void shouldSendMqWhenReceiveSuccessfully() {
        stubScriptResult(0L);

        UserContext.setUser(1L);
        userCouponService.receiveCoupon(COUPON_ID);

        verify(mqHelper).send(eq("promotion.topic"), eq("coupon.receive"), any(UserCouponDTO.class));
    }

    @Test
    void shouldRejectWhenReceiveScriptReturnsError() {
        stubScriptResult(4L);

        UserContext.setUser(1L);

        BizIllegalException e = assertThrows(BizIllegalException.class,
                () -> userCouponService.receiveCoupon(COUPON_ID));
        org.junit.jupiter.api.Assertions.assertEquals("超出领取数量", e.getMessage());
        verify(mqHelper, never()).send(any(), any(), any());
    }

    @Test
    void shouldRollbackRedisWhenMqSendFails() {
        stubScriptResult(0L);
        when(redisTemplate.opsForHash()).thenReturn(hashOperations);
        doThrow(new RuntimeException("mq error"))
                .when(mqHelper).send(anyString(), anyString(), any());

        UserContext.setUser(1L);

        assertThrows(RuntimeException.class, () -> userCouponService.receiveCoupon(COUPON_ID));
        verify(hashOperations).increment("prs:coupon:" + COUPON_ID, "totalNum", 1);
        verify(hashOperations).increment("prs:user:coupon:" + COUPON_ID, "1", -1);
    }

    @Test
    void shouldSendMqWhenExchangeSuccessfully() {
        String code = CodeUtil.generateCode(SERIAL_NUM, COUPON_ID);
        stubScriptResult(String.valueOf(COUPON_ID));

        UserContext.setUser(1L);
        userCouponService.exchangeCoupon(code);

        verify(mqHelper).send(eq("promotion.topic"), eq("coupon.receive"), any(UserCouponDTO.class));
    }

    @Test
    void shouldRejectWhenExchangeScriptReturnsError() {
        String code = CodeUtil.generateCode(SERIAL_NUM, COUPON_ID);
        stubScriptResult("1");

        UserContext.setUser(1L);

        BizIllegalException e = assertThrows(BizIllegalException.class,
                () -> userCouponService.exchangeCoupon(code));
        org.junit.jupiter.api.Assertions.assertEquals("兑换码已经被兑换过了", e.getMessage());
        verify(mqHelper, never()).send(any(), any(), any());
    }

    @Test
    void writeOffShouldOnlyCountValidCoupons() {
        LocalDateTime now = LocalDateTime.now();
        UserCoupon unused = userCoupon(1L, 10L, UserCouponStatus.UNUSED, now.minusDays(1), now.plusDays(1));
        UserCoupon used = userCoupon(2L, 20L, UserCouponStatus.USED, now.minusDays(1), now.plusDays(1));
        doReturn(Arrays.asList(unused, used)).when(userCouponService).listByIds(anyList());
        doReturn(true).when(userCouponService).updateBatchById(anyList());
        when(couponMapper.incrUsedNum(List.of(10L), 1)).thenReturn(1);

        userCouponService.writeOffCoupon(List.of(1L, 2L));

        verify(couponMapper).incrUsedNum(List.of(10L), 1);
        verify(couponMapper, never()).incrUsedNum(List.of(20L), 1);
    }

    @Test
    void refundShouldOnlyRevertUsedCoupons() {
        LocalDateTime now = LocalDateTime.now();
        UserCoupon used = userCoupon(1L, 10L, UserCouponStatus.USED, now.minusDays(1), now.plusDays(1));
        UserCoupon unused = userCoupon(2L, 20L, UserCouponStatus.UNUSED, now.minusDays(1), now.plusDays(1));
        doReturn(Arrays.asList(used, unused)).when(userCouponService).listByIds(anyList());
        doReturn(true).when(userCouponService).updateBatchById(anyList());
        when(couponMapper.incrUsedNum(List.of(10L), -1)).thenReturn(1);

        userCouponService.refundCoupon(List.of(1L, 2L));

        verify(couponMapper).incrUsedNum(List.of(10L), -1);
        verify(couponMapper, never()).incrUsedNum(List.of(20L), -1);
    }

    @SuppressWarnings("unchecked")
    private void stubScriptResult(Object result) {
        when(redisTemplate.execute(any(RedisScript.class), anyList(), any())).thenReturn(result);
    }

    private UserCoupon userCoupon(Long id, Long couponId, UserCouponStatus status,
                                  LocalDateTime begin, LocalDateTime end) {
        return new UserCoupon()
                .setId(id)
                .setCouponId(couponId)
                .setStatus(status)
                .setTermBeginTime(begin)
                .setTermEndTime(end);
    }
}