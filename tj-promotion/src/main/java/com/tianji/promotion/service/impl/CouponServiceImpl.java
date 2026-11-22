package com.tianji.promotion.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tianji.api.cache.CategoryCache;
import com.tianji.api.dto.course.CategoryBasicDTO;
import com.tianji.common.domain.dto.PageDTO;
import com.tianji.common.exceptions.BadRequestException;
import com.tianji.common.exceptions.BizIllegalException;
import com.tianji.common.utils.BeanUtils;
import com.tianji.common.utils.CollUtils;
import com.tianji.common.utils.StringUtils;
import com.tianji.common.utils.UserContext;
import com.tianji.promotion.domain.dto.CouponFormDTO;
import com.tianji.promotion.domain.dto.CouponIssueFormDTO;
import com.tianji.promotion.domain.po.Coupon;
import com.tianji.promotion.domain.po.CouponScope;
import com.tianji.promotion.domain.po.UserCoupon;
import com.tianji.promotion.domain.query.CouponQuery;
import com.tianji.promotion.domain.vo.CouponDetailVO;
import com.tianji.promotion.domain.vo.CouponPageVO;
import com.tianji.promotion.domain.vo.CouponScopeVO;
import com.tianji.promotion.domain.vo.CouponVO;
import com.tianji.promotion.enums.CouponStatus;
import com.tianji.promotion.enums.ObtainType;
import com.tianji.promotion.enums.UserCouponStatus;
import com.tianji.promotion.mapper.CouponMapper;
import com.tianji.promotion.service.ICouponScopeService;
import com.tianji.promotion.service.ICouponService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.tianji.promotion.service.IExchangeCodeService;
import com.tianji.promotion.service.IUserCouponService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import static com.tianji.promotion.constants.PromotionConstants.COUPON_CACHE_KEY_PREFIX;
import static com.tianji.promotion.enums.CouponStatus.*;

/**
 * <p>
 * 优惠券的规则信息 服务实现类
 * </p>
 *
 * @author Tom
 * @since 2026-11-18
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CouponServiceImpl extends ServiceImpl<CouponMapper, Coupon> implements ICouponService {

    private final ICouponScopeService scopeService;
    private final IExchangeCodeService codeService;
    private final CategoryCache categoryCache;
    private final IUserCouponService userCouponService;
    private final StringRedisTemplate redisTemplate;

    @Override
    @Transactional
    public void saveCoupon(CouponFormDTO dto) {
        // 1.保存优惠券
        // 1.1.转PO
        Coupon coupon = BeanUtils.copyBean(dto, Coupon.class);
        // 1.2.保存
        save(coupon);

        if (!dto.getSpecific()) {
            // 没有范围限定
            return;
        }
        // 2.保存限定范围
        saveScopes(coupon.getId(), dto.getScopes());
    }

    @Override
    @Transactional
    public void updateCoupon(CouponFormDTO dto) {
        // 1.查询优惠券
        Coupon coupon = getById(dto.getId());
        if (coupon == null) {
            throw new BadRequestException("优惠券不存在！");
        }
        // 2.只有待发放的优惠券可以修改
        if (coupon.getStatus() != DRAFT) {
            throw new BizIllegalException("只有待发放状态的优惠券才能修改！");
        }
        // 3.更新优惠券本身的信息
        Coupon c = BeanUtils.copyBean(dto, Coupon.class);
        updateById(c);
        // 4.更新限定的作用范围
        // 4.1.删除原有的范围
        scopeService.remove(new LambdaQueryWrapper<CouponScope>()
                .eq(CouponScope::getCouponId, dto.getId()));
        if (!dto.getSpecific()) {
            // 不再限定范围，无需保存
            return;
        }
        // 4.2.保存新的范围
        saveScopes(dto.getId(), dto.getScopes());
    }

    @Override
    @Transactional
    public void deleteCoupon(Long id) {
        // 1.查询优惠券
        Coupon coupon = getById(id);
        if (coupon == null) {
            throw new BadRequestException("优惠券不存在！");
        }
        // 2.只有待发放的优惠券可以删除
        if (coupon.getStatus() != DRAFT) {
            throw new BizIllegalException("只有待发放状态的优惠券才能删除！");
        }
        // 3.删除优惠券
        removeById(id);
        // 4.删除限定的作用范围
        scopeService.remove(new LambdaQueryWrapper<CouponScope>()
                .eq(CouponScope::getCouponId, id));
    }

    @Override
    public CouponDetailVO queryCouponById(Long id) {
        // 1.查询优惠券
        Coupon coupon = getById(id);
        if (coupon == null) {
            throw new BadRequestException("优惠券不存在！");
        }
        // 2.转换VO
        CouponDetailVO vo = BeanUtils.copyBean(coupon, CouponDetailVO.class);
        if (!coupon.getSpecific()) {
            // 没有限定范围，无需查询
            return vo;
        }
        // 3.查询限定的作用范围
        List<CouponScope> scopes = scopeService.lambdaQuery()
                .eq(CouponScope::getCouponId, id)
                .list();
        if (CollUtils.isEmpty(scopes)) {
            return vo;
        }
        // 4.根据分类id查询分类名称，用于表单回显
        Map<Long, CategoryBasicDTO> categoryMap = categoryCache.getCategoryMap();
        List<CouponScopeVO> scopeVOs = scopes.stream()
                .map(scope -> categoryMap.get(scope.getBizId()))
                .filter(Objects::nonNull)
                .map(category -> new CouponScopeVO(category.getId(), category.getName()))
                .collect(Collectors.toList());
        vo.setScopes(scopeVOs);
        return vo;
    }

    @Override
    public PageDTO<CouponPageVO> queryCouponByPage(CouponQuery query) {
        Integer status = query.getStatus();
        String name = query.getName();
        Integer type = query.getType();
        // 1.分页查询
        Page<Coupon> page = lambdaQuery()
                .eq(type != null, Coupon::getDiscountType, type)
                .eq(status != null, Coupon::getStatus, status)
                .like(StringUtils.isNotBlank(name), Coupon::getName, name)
                .page(query.toMpPageDefaultSortByCreateTimeDesc());
        // 2.处理VO
        List<Coupon> records = page.getRecords();
        if (CollUtils.isEmpty(records)) {
            return PageDTO.empty(page);
        }
        List<CouponPageVO> list = BeanUtils.copyList(records, CouponPageVO.class);
        // 3.返回
        return PageDTO.of(page, list);
    }


    @Transactional
    @Override
    public void beginIssue(CouponIssueFormDTO dto) {
        // 1.查询优惠券
        Coupon coupon = getById(dto.getId());
        if (coupon == null) {
            throw new BadRequestException("优惠券不存在！");
        }
        // 2.判断优惠券状态，是否是暂停或待发放
        if(coupon.getStatus() != CouponStatus.DRAFT && coupon.getStatus() != PAUSE){
            throw new BizIllegalException("优惠券状态错误！");
        }
        // 3.判断是否是立刻发放
        LocalDateTime issueBeginTime = dto.getIssueBeginTime();
        LocalDateTime now = LocalDateTime.now();
        boolean isBegin = issueBeginTime == null || !issueBeginTime.isAfter(now);
        // 4.更新优惠券
        // 4.1.拷贝属性到PO
        Coupon c = BeanUtils.copyBean(dto, Coupon.class);
        // 4.2.更新状态
        if (isBegin) {
            c.setStatus(ISSUING);
            c.setIssueBeginTime(now);
        }else{
            c.setStatus(UN_ISSUE);
        }
        // 4.3.写入数据库
        updateById(c);

        // 5.添加缓存，前提是立刻发放的
        if (isBegin) {
            coupon.setIssueBeginTime(c.getIssueBeginTime());
            coupon.setIssueEndTime(c.getIssueEndTime());
            cacheCouponInfo(coupon);
        }

        // 6.判断是否需要生成兑换码，优惠券类型必须是兑换码，优惠券状态必须是待发放
        if(coupon.getObtainWay() == ObtainType.ISSUE && coupon.getStatus() == CouponStatus.DRAFT){
            coupon.setIssueEndTime(c.getIssueEndTime());
            codeService.asyncGenerateCode(coupon);
        }
    }

    @Override
    @Transactional
    public void pauseCoupon(Long id) {
        // 1.查询优惠券
        Coupon coupon = getById(id);
        if (coupon == null) {
            throw new BadRequestException("优惠券不存在！");
        }
        // 2.只有未开始或发放中的优惠券可以暂停
        CouponStatus status = coupon.getStatus();
        if (status != UN_ISSUE && status != ISSUING) {
            // 状态不正确，直接结束（可能是重复暂停）
            return;
        }
        // 3.更新状态：未开始/发放中 -> 暂停，带上状态条件避免并发重复更新
        boolean success = lambdaUpdate()
                .set(Coupon::getStatus, PAUSE)
                .eq(Coupon::getId, id)
                .in(Coupon::getStatus, UN_ISSUE, ISSUING)
                .update();
        if (!success) {
            // 可能是重复更新，结束
            log.error("重复暂停优惠券");
        }
        // 4.删除缓存，下次重新发放时再重建
        redisTemplate.delete(cacheKey(id));
    }

    @Override
    @Transactional
    public int updateIssueBegin(int shardIndex, int shardTotal) {
        // 1.查询本分片中已经到了发放开始时间、但仍未开始的优惠券
        LocalDateTime now = LocalDateTime.now();
        List<Coupon> coupons = lambdaQuery()
                .eq(Coupon::getStatus, UN_ISSUE)
                .le(Coupon::getIssueBeginTime, now)
                .apply("MOD(id, {0}) = {1}", shardTotal, shardIndex)
                .list();
        if (CollUtils.isEmpty(coupons)) {
            return 0;
        }
        int count = 0;
        for (Coupon coupon : coupons) {
            // 2.逐张更新状态，带上原状态条件，避免与暂停等操作并发覆盖
            boolean success = lambdaUpdate()
                    .set(Coupon::getStatus, ISSUING)
                    .eq(Coupon::getId, coupon.getId())
                    .eq(Coupon::getStatus, UN_ISSUE)
                    .update();
            if (!success) {
                // 已经被其它操作修改，跳过
                continue;
            }
            // 3.发放开始的同时添加缓存
            cacheCouponInfo(coupon);
            count++;
        }
        return count;
    }

    @Override
    @Transactional
    public int updateIssueEnd(int shardIndex, int shardTotal) {
        // 1.查询本分片中已经到了发放结束时间、但仍在发放中的优惠券
        LocalDateTime now = LocalDateTime.now();
        List<Coupon> coupons = lambdaQuery()
                .eq(Coupon::getStatus, ISSUING)
                .le(Coupon::getIssueEndTime, now)
                .apply("MOD(id, {0}) = {1}", shardTotal, shardIndex)
                .list();
        if (CollUtils.isEmpty(coupons)) {
            return 0;
        }
        int count = 0;
        for (Coupon coupon : coupons) {
            // 2.逐张更新状态，带上原状态条件，避免与暂停等操作并发覆盖
            boolean success = lambdaUpdate()
                    .set(Coupon::getStatus, FINISHED)
                    .eq(Coupon::getId, coupon.getId())
                    .eq(Coupon::getStatus, ISSUING)
                    .update();
            if (!success) {
                // 已经被其它操作修改，跳过
                continue;
            }
            // 3.发放结束后移除缓存
            redisTemplate.delete(cacheKey(coupon.getId()));
            count++;
        }
        return count;
    }

    /**
     * 缓存优惠券的发放时间、库存、每人限领数量和获取方式。
     * 库存字段 totalNum 中保存的是剩余库存（总量 - 已发放），领取时通过 HINCRBY 扣减。
     * 时间保存为秒级时间戳，供 LUA 脚本与 redis.call('time')[1] 直接比较。
     */
    private void cacheCouponInfo(Coupon coupon) {
        if (coupon.getIssueBeginTime() == null || coupon.getIssueEndTime() == null) {
            log.warn("优惠券[{}]的发放时间不完整，跳过缓存", coupon.getId());
            return;
        }
        // 1.组织数据
        int issued = coupon.getIssueNum() == null ? 0 : coupon.getIssueNum();
        int remaining = Math.max(coupon.getTotalNum() - issued, 0);
        Map<String, String> map = new HashMap<>(5);
        map.put("issueBeginTime", String.valueOf(coupon.getIssueBeginTime().toEpochSecond(ZoneOffset.ofHours(8))));
        map.put("issueEndTime", String.valueOf(coupon.getIssueEndTime().toEpochSecond(ZoneOffset.ofHours(8))));
        map.put("totalNum", String.valueOf(remaining));
        map.put("userLimit", String.valueOf(coupon.getUserLimit()));
        // 兑换码券只能通过兑换码兑换，不能走领券接口，需要缓存获取方式
        if (coupon.getObtainWay() != null) {
            map.put("obtainWay", coupon.getObtainWay().name());
        }
        // 2.写缓存
        redisTemplate.opsForHash().putAll(cacheKey(coupon.getId()), map);
    }

    /**
     * 优惠券缓存KEY：prs:coupon:{couponId}
     */
    private String cacheKey(Long couponId) {
        return COUPON_CACHE_KEY_PREFIX + couponId;
    }

    /**
     * 保存优惠券的限定使用范围
     *
     * @param couponId 优惠券id
     * @param scopes   分类id集合
     */
    private void saveScopes(Long couponId, List<Long> scopes) {
        if (CollUtils.isEmpty(scopes)) {
            throw new BadRequestException("限定范围不能为空");
        }
        List<CouponScope> list = scopes.stream()
                .map(bizId -> new CouponScope()
                        .setBizId(bizId)
                        .setCouponId(couponId)
                        // 范围类型：1-分类
                        .setType(1))
                .collect(Collectors.toList());
        scopeService.saveBatch(list);
    }


    @Override
    public List<CouponVO> queryIssuingCoupons() {
        // 1.查询发放中的优惠券列表
        List<Coupon> coupons = lambdaQuery()
                .eq(Coupon::getStatus, ISSUING)
                .eq(Coupon::getObtainWay, ObtainType.PUBLIC)
                .list();
        if (CollUtils.isEmpty(coupons)) {
            return CollUtils.emptyList();
        }
        // 2.统计当前用户已经领取的优惠券的信息
        List<Long> couponIds = coupons.stream().map(Coupon::getId).collect(Collectors.toList());
        // 2.1.查询当前用户已经领取的优惠券的数据
        List<UserCoupon> userCoupons = userCouponService.lambdaQuery()
                .eq(UserCoupon::getUserId, UserContext.getUser())
                .in(UserCoupon::getCouponId, couponIds)
                .list();
        // 2.2.统计当前用户对优惠券的已经领取数量
        Map<Long, Long> issuedMap = userCoupons.stream()
                .collect(Collectors.groupingBy(UserCoupon::getCouponId, Collectors.counting()));
        // 2.3.统计当前用户对优惠券的已经领取并且未使用的数量
        Map<Long, Long> unusedMap = userCoupons.stream()
                .filter(uc -> uc.getStatus() == UserCouponStatus.UNUSED)
                .collect(Collectors.groupingBy(UserCoupon::getCouponId, Collectors.counting()));
        // 3.封装VO结果
        List<CouponVO> list = new ArrayList<>(coupons.size());
        for (Coupon c : coupons) {
            // 3.1.拷贝PO属性到VO
            CouponVO vo = BeanUtils.copyBean(c, CouponVO.class);
            list.add(vo);
            // 3.2.是否可以领取：已经被领取的数量 < 优惠券总数量 && 当前用户已经领取的数量 < 每人限领数量
            vo.setAvailable(
                    c.getIssueNum() < c.getTotalNum()
                            && issuedMap.getOrDefault(c.getId(), 0L) < c.getUserLimit()
            );
            // 3.3.是否可以使用：当前用户已经领取并且未使用的优惠券数量 > 0
            vo.setReceived(unusedMap.getOrDefault(c.getId(),  0L) > 0);
        }
        return list;
    }
}