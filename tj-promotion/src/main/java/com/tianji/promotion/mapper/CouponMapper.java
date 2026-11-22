package com.tianji.promotion.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.tianji.promotion.domain.po.Coupon;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.util.List;


public interface CouponMapper extends BaseMapper<Coupon> {
    @Update("UPDATE coupon SET issue_num = issue_num + 1 WHERE id = #{couponId} AND issue_num < total_num")
    int incrIssueNum(@Param("couponId") Long couponId);

    /**
     * 批量更新优惠券的已使用数量
     *
     * @param couponIds 优惠券id集合
     * @param increment 增量，核销传1，退还传-1
     * @return 更新的记录数
     */
    @Update("<script>" +
            "UPDATE coupon SET used_num = used_num + #{increment} WHERE id IN " +
            "<foreach collection='couponIds' item='couponId' open='(' separator=',' close=')'>" +
            "#{couponId}" +
            "</foreach>" +
            "</script>")
    int incrUsedNum(@Param("couponIds") List<Long> couponIds, @Param("increment") int increment);
}