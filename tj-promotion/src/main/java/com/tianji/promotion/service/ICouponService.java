package com.tianji.promotion.service;

import com.tianji.common.domain.dto.PageDTO;
import com.tianji.promotion.domain.dto.CouponFormDTO;
import com.tianji.promotion.domain.dto.CouponIssueFormDTO;
import com.tianji.promotion.domain.po.Coupon;
import com.baomidou.mybatisplus.extension.service.IService;
import com.tianji.promotion.domain.query.CouponQuery;
import com.tianji.promotion.domain.vo.CouponDetailVO;
import com.tianji.promotion.domain.vo.CouponPageVO;
import com.tianji.promotion.domain.vo.CouponVO;

import javax.validation.Valid;
import java.util.List;

/**
 * <p>
 * 优惠券的规则信息 服务类
 * </p>
 *
 * @author Tom
 * @since 2026-11-18
 */
public interface ICouponService extends IService<Coupon> {

    void saveCoupon(@Valid CouponFormDTO dto);

    void updateCoupon(@Valid CouponFormDTO dto);

    void deleteCoupon(Long id);

    CouponDetailVO queryCouponById(Long id);

    PageDTO<CouponPageVO> queryCouponByPage(CouponQuery query);

    void beginIssue(@Valid CouponIssueFormDTO dto);

    void pauseCoupon(Long id);

    /**
     * 将到了发放开始时间的优惠券由“未开始”修改为“发放中”，并添加优惠券缓存，
     * 通过 xxl-job 的分片参数保证每张优惠券只被一个执行器处理
     *
     * @param shardIndex 当前分片序号
     * @param shardTotal 分片总数
     * @return 更新的优惠券数量
     */
    int updateIssueBegin(int shardIndex, int shardTotal);

    /**
     * 将到了发放结束时间的优惠券由“发放中”修改为“发放结束”，并移除优惠券缓存，
     * 通过 xxl-job 的分片参数保证每张优惠券只被一个执行器处理
     *
     * @param shardIndex 当前分片序号
     * @param shardTotal 分片总数
     * @return 更新的优惠券数量
     */
    int updateIssueEnd(int shardIndex, int shardTotal);

    List<CouponVO> queryIssuingCoupons();
}