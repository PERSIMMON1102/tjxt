package com.tianji.promotion.service.impl;

import com.tianji.api.dto.promotion.CouponDiscountDTO;
import com.tianji.api.dto.promotion.OrderCouponDTO;
import com.tianji.api.dto.promotion.OrderCourseDTO;
import com.tianji.common.utils.UserContext;
import com.tianji.promotion.domain.po.Coupon;
import com.tianji.promotion.enums.DiscountType;
import com.tianji.promotion.enums.UserCouponStatus;
import com.tianji.promotion.mapper.UserCouponMapper;
import com.tianji.promotion.service.ICouponScopeService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Executor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DiscountServiceImplTest {

    private final UserCouponMapper userCouponMapper = mock(UserCouponMapper.class);
    private final ICouponScopeService scopeService = mock(ICouponScopeService.class);
    // 同步执行，避免异步线程带来的测试不确定性
    private final Executor executor = Runnable::run;
    private final DiscountServiceImpl discountService =
            new DiscountServiceImpl(userCouponMapper, scopeService, executor);

    @AfterEach
    void tearDown() {
        UserContext.removeUser();
    }

    @Test
    void queryDiscountDetailShouldIgnoreUnavailableCoupon() {
        OrderCourseDTO course = new OrderCourseDTO().setId(1L).setCateId(10L).setPrice(10000);
        // 101 可用，102 门槛高于订单金额，不可用
        Coupon available = coupon(1001L, 101L, 10000, 1000);
        Coupon unavailable = coupon(1002L, 102L, 20000, 2000);
        when(userCouponMapper.queryCouponByUserCouponIds(anyList(), eq(UserCouponStatus.UNUSED)))
                .thenReturn(Arrays.asList(available, unavailable));

        OrderCouponDTO dto = new OrderCouponDTO(
                Arrays.asList(101L, 102L), List.of(course));

        CouponDiscountDTO result = discountService.queryDiscountDetailByOrder(dto);

        assertNotNull(result);
        assertEquals(1000, result.getDiscountAmount());
        assertEquals(List.of(101L), result.getIds());
        assertEquals(1000, result.getDiscountDetail().get(1L));
    }

    @Test
    void findDiscountSolutionShouldReturnBestSolution() {
        Coupon coupon = coupon(1001L, 101L, 10000, 1000);
        when(userCouponMapper.queryMyCoupons(1L)).thenReturn(List.of(coupon));

        UserContext.setUser(1L);
        List<CouponDiscountDTO> solutions = discountService.findDiscountSolution(
                List.of(new OrderCourseDTO().setId(1L).setCateId(10L).setPrice(10000)));

        assertEquals(1, solutions.size());
        assertEquals(1000, solutions.get(0).getDiscountAmount());
        assertEquals(List.of(101L), solutions.get(0).getIds());
    }

    private Coupon coupon(Long couponId, Long userCouponId, Integer threshold, Integer discount) {
        Coupon coupon = new Coupon();
        coupon.setId(couponId);
        coupon.setCreater(userCouponId);
        coupon.setDiscountType(DiscountType.PRICE_DISCOUNT);
        coupon.setThresholdAmount(threshold);
        coupon.setDiscountValue(discount);
        coupon.setSpecific(false);
        return coupon;
    }
}