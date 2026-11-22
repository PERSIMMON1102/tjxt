package com.tianji.promotion.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.tianji.api.client.user.UserClient;
import com.tianji.api.dto.user.UserDTO;
import com.tianji.common.autoconfigure.mq.RabbitMqHelper;
import com.tianji.common.constants.MqConstants;
import com.tianji.common.domain.dto.PageDTO;
import com.tianji.common.exceptions.BizIllegalException;
import com.tianji.common.exceptions.DbException;
import com.tianji.common.utils.BeanUtils;
import com.tianji.common.utils.CollUtils;
import com.tianji.common.utils.NumberUtils;
import com.tianji.common.utils.StringUtils;
import com.tianji.common.utils.UserContext;
import com.tianji.message.api.client.AsyncSmsClient;
import com.tianji.message.domain.dto.SmsInfoDTO;
import com.tianji.message.domain.enums.SmsTemplate;
import com.tianji.promotion.domain.dto.UserCouponDTO;
import com.tianji.promotion.domain.po.Coupon;
import com.tianji.promotion.domain.po.ExchangeCode;
import com.tianji.promotion.domain.po.UserCoupon;
import com.tianji.promotion.domain.query.UserCouponQuery;
import com.tianji.promotion.domain.vo.UserCouponVO;
import com.tianji.promotion.enums.ExchangeCodeStatus;
import com.tianji.promotion.enums.UserCouponStatus;
import com.tianji.promotion.mapper.CouponMapper;
import com.tianji.promotion.mapper.UserCouponMapper;
import com.tianji.promotion.service.IExchangeCodeService;
import com.tianji.promotion.service.IUserCouponService;
import com.tianji.promotion.strategy.discount.DiscountStrategy;
import com.tianji.promotion.utils.CodeUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.tianji.promotion.constants.PromotionConstants.COUPON_CACHE_KEY_PREFIX;
import static com.tianji.promotion.constants.PromotionConstants.COUPON_CODE_MAP_KEY;
import static com.tianji.promotion.constants.PromotionConstants.COUPON_RANGE_KEY;
import static com.tianji.promotion.constants.PromotionConstants.COUPON_REMIND_KEY;
import static com.tianji.promotion.constants.PromotionConstants.EXCHANGE_COUPON_ERROR_MSG;
import static com.tianji.promotion.constants.PromotionConstants.RECEIVE_COUPON_ERROR_MSG;
import static com.tianji.promotion.constants.PromotionConstants.USER_COUPON_CACHE_KEY_PREFIX;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserCouponServiceImpl extends ServiceImpl<UserCouponMapper, UserCoupon> implements IUserCouponService {

    /**
     * 优惠券过期前多少天提醒
     */
    private static final int REMIND_DAYS = 3;

    private static final DateTimeFormatter REMIND_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    /**
     * 领券资格校验脚本：校验优惠券、库存、时间、限领数量，并扣减库存
     */
    private static final RedisScript<Long> RECEIVE_COUPON_SCRIPT;

    /**
     * 兑换码资格校验脚本：校验兑换码、活动时间、限领数量，并标记兑换码已使用
     */
    private static final RedisScript<String> EXCHANGE_COUPON_SCRIPT;

    static {
        RECEIVE_COUPON_SCRIPT = RedisScript.of(new ClassPathResource("lua/receive_coupon.lua"), Long.class);
        EXCHANGE_COUPON_SCRIPT = RedisScript.of(new ClassPathResource("lua/exchange_coupon.lua"), String.class);
    }

    private final CouponMapper couponMapper;

    private final IExchangeCodeService codeService;

    private final StringRedisTemplate redisTemplate;

    private final UserClient userClient;

    private final AsyncSmsClient asyncSmsClient;

    private final RabbitMqHelper mqHelper;

    @Override
    public void receiveCoupon(Long couponId) {
        // 1.执行LUA脚本，一次请求完成资格校验
        Long userId = UserContext.getUser();
        String couponKey = COUPON_CACHE_KEY_PREFIX + couponId;
        String userKey = USER_COUPON_CACHE_KEY_PREFIX + couponId;
        Long r = redisTemplate.execute(
                RECEIVE_COUPON_SCRIPT,
                Arrays.asList(couponKey, userKey),
                userId.toString());
        int result = NumberUtils.null2Zero(r).intValue();
        if (result != 0) {
            // 返回值大于0，说明校验失败，取对应错误信息
            throw new BizIllegalException(errorMsg(RECEIVE_COUPON_ERROR_MSG, result, "领取优惠券失败"));
        }
        // 2.发送MQ消息，异步更新数据库
        UserCouponDTO uc = new UserCouponDTO();
        uc.setUserId(userId);
        uc.setCouponId(couponId);
        try {
            mqHelper.send(MqConstants.Exchange.PROMOTION_EXCHANGE, MqConstants.Key.COUPON_RECEIVE, uc);
        } catch (Exception e) {
            // 消息发送失败，回滚脚本中扣减的库存和领取数量，允许用户重试
            redisTemplate.opsForHash().increment(couponKey, "totalNum", 1);
            redisTemplate.opsForHash().increment(userKey, userId.toString(), -1);
            throw e;
        }
    }

    @Override
    public void exchangeCoupon(String code) {
        // 1.校验并解析兑换码
        long serialNum = CodeUtil.parseCode(code);
        Long userId = UserContext.getUser();
        // 2.执行LUA脚本，一次请求完成资格校验并标记兑换码已使用
        String result = redisTemplate.execute(
                EXCHANGE_COUPON_SCRIPT,
                Arrays.asList(COUPON_CODE_MAP_KEY, COUPON_RANGE_KEY),
                String.valueOf(serialNum), String.valueOf(serialNum + 5000), userId.toString());
        if (StringUtils.isBlank(result)) {
            throw new BizIllegalException("兑换优惠券失败");
        }
        long r;
        try {
            r = Long.parseLong(result);
        } catch (NumberFormatException e) {
            throw new BizIllegalException("兑换优惠券失败");
        }
        if (r < 10) {
            // 返回值1~5表示校验失败
            throw new BizIllegalException(errorMsg(EXCHANGE_COUPON_ERROR_MSG, (int) r, "兑换优惠券失败"));
        }
        // 3.发送MQ消息通知
        Long couponId = r;
        UserCouponDTO uc = new UserCouponDTO();
        uc.setUserId(userId);
        uc.setCouponId(couponId);
        uc.setSerialNum((int) serialNum);
        try {
            mqHelper.send(MqConstants.Exchange.PROMOTION_EXCHANGE, MqConstants.Key.COUPON_RECEIVE, uc);
        } catch (Exception e) {
            // 消息发送失败，恢复兑换码标记和用户领取数量，允许用户重试
            codeService.updateExchangeMark(serialNum, false);
            redisTemplate.opsForHash().increment(USER_COUPON_CACHE_KEY_PREFIX + couponId, userId.toString(), -1);
            throw e;
        }
    }

    /**
     * 根据脚本返回值获取错误信息
     */
    private String errorMsg(String[] messages, int code, String defaultMsg) {
        if (code < 1 || code > messages.length) {
            return defaultMsg;
        }
        return messages[code - 1];
    }

    @Transactional // 这里进事务，同时，事务方法一定要public修饰
    @Override
    public void checkAndCreateUserCoupon(UserCouponDTO uc) {
        // 1.查询优惠券
        Coupon coupon = couponMapper.selectById(uc.getCouponId());
        if (coupon == null) {
            throw new BizIllegalException("优惠券不存在！");
        }
        // 2.更新优惠券的已经发放的数量 + 1
        int r = couponMapper.incrIssueNum(coupon.getId());
        if (r == 0) {
            throw new BizIllegalException("优惠券库存不足！");
        }
        // 3.新增一个用户券
        saveUserCoupon(coupon, uc.getUserId());
        // 4.更新兑换码状态
        if (uc.getSerialNum() != null) {
            codeService.lambdaUpdate()
                    .set(ExchangeCode::getUserId, uc.getUserId())
                    .set(ExchangeCode::getStatus, ExchangeCodeStatus.USED)
                    .eq(ExchangeCode::getId, uc.getSerialNum())
                    .update();
        }
    }

    @Override
    public PageDTO<UserCouponVO> queryMyCoupons(UserCouponQuery query) {
        // 1.分页查询当前用户的用户券
        Page<UserCoupon> page = lambdaQuery()
                .eq(UserCoupon::getUserId, UserContext.getUser())
                .eq(query.getStatus() != null, UserCoupon::getStatus, query.getStatus())
                .page(query.toMpPageDefaultSortByCreateTimeDesc());
        List<UserCoupon> records = page.getRecords();
        if (CollUtils.isEmpty(records)) {
            return PageDTO.empty(page);
        }
        // 2.批量查询优惠券信息
        List<Long> couponIds = records.stream()
                .map(UserCoupon::getCouponId)
                .distinct()
                .collect(Collectors.toList());
        List<Coupon> coupons = couponMapper.selectBatchIds(couponIds);
        if (CollUtils.isEmpty(coupons)) {
            return PageDTO.empty(page);
        }
        Map<Long, Coupon> couponMap = coupons.stream()
                .collect(Collectors.toMap(Coupon::getId, Function.identity()));
        // 3.组装VO
        List<UserCouponVO> list = new ArrayList<>(records.size());
        for (UserCoupon uc : records) {
            Coupon coupon = couponMap.get(uc.getCouponId());
            if (coupon == null) {
                // 优惠券模板已经不存在，跳过
                continue;
            }
            UserCouponVO vo = BeanUtils.copyBean(coupon, UserCouponVO.class);
            // 3.1.用户券id
            vo.setId(uc.getId());
            // 3.2.过期时间以用户券为准（按有效天数领取的券，模板中没有过期时间）
            vo.setTermEndTime(uc.getTermEndTime());
            list.add(vo);
        }
        return PageDTO.of(page, list);
    }

    @Override
    public int remindExpiringCoupons(int shardIndex, int shardTotal) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime deadline = now.plusDays(REMIND_DAYS);
        // 1.查询指定天数内即将过期、且未使用的用户券，按分片处理，避免多个执行器重复扫描
        List<UserCoupon> expiring = lambdaQuery()
                .eq(UserCoupon::getStatus, UserCouponStatus.UNUSED)
                .ge(UserCoupon::getTermEndTime, now)
                .le(UserCoupon::getTermEndTime, deadline)
                .apply("MOD(id, {0}) = {1}", shardTotal, shardIndex)
                .list();
        if (CollUtils.isEmpty(expiring)) {
            return 0;
        }
        // 2.幂等控制：同一张用户券只提醒一次，用Redis标记，避免任务重复执行导致重复发短信
        List<UserCoupon> toRemind = new ArrayList<>(expiring.size());
        for (UserCoupon uc : expiring) {
            Boolean first = redisTemplate.opsForValue()
                    .setIfAbsent(COUPON_REMIND_KEY + uc.getId(), "1", Duration.ofDays(REMIND_DAYS + 1L));
            if (Boolean.TRUE.equals(first)) {
                toRemind.add(uc);
            }
        }
        if (toRemind.isEmpty()) {
            return 0;
        }
        // 3.按用户聚合：一个用户只发一条短信
        Map<Long, List<UserCoupon>> userCouponMap = toRemind.stream()
                .collect(Collectors.groupingBy(UserCoupon::getUserId));
        // 4.批量查询用户手机号
        List<UserDTO> users = userClient.queryUserByIds(userCouponMap.keySet());
        Map<Long, String> phoneMap = CollUtils.isEmpty(users) ?
                new HashMap<>() :
                users.stream()
                        .filter(u -> u.getId() != null && StringUtils.isNotBlank(u.getCellPhone()))
                        .collect(Collectors.toMap(UserDTO::getId, UserDTO::getCellPhone));
        // 5.逐个用户发送提醒短信
        int reminded = 0;
        for (Map.Entry<Long, List<UserCoupon>> entry : userCouponMap.entrySet()) {
            List<UserCoupon> coupons = entry.getValue();
            String phone = phoneMap.get(entry.getKey());
            if (StringUtils.isBlank(phone)) {
                // 没有手机号，释放标记，等下次任务再提醒
                releaseRemindMarks(coupons);
                continue;
            }
            try {
                asyncSmsClient.sendMessage(buildRemindSms(phone, coupons));
                reminded += coupons.size();
            } catch (Exception e) {
                // 发送失败，释放标记，等下次任务重试
                releaseRemindMarks(coupons);
                log.error("优惠券过期提醒短信发送失败，userId={}", entry.getKey(), e);
            }
        }
        return reminded;
    }

    /**
     * 组织过期提醒短信，多个优惠券合并为一条短信
     */
    private SmsInfoDTO buildRemindSms(String phone, List<UserCoupon> coupons) {
        LocalDateTime earliest = coupons.stream()
                .map(UserCoupon::getTermEndTime)
                .min(LocalDateTime::compareTo)
                .orElse(LocalDateTime.now());
        Map<String, String> params = new HashMap<>(2);
        params.put("count", String.valueOf(coupons.size()));
        params.put("expireTime", earliest.format(REMIND_TIME_FORMATTER));

        SmsInfoDTO smsInfo = new SmsInfoDTO();
        smsInfo.setPhones(CollUtils.singletonList(phone));
        smsInfo.setTemplateCode(SmsTemplate.COUPON_EXPIRE_REMIND.toString());
        smsInfo.setTemplateParams(params);
        return smsInfo;
    }

    /**
     * 释放提醒标记，让下次任务可以重新提醒
     */
    private void releaseRemindMarks(List<UserCoupon> coupons) {
        List<String> keys = coupons.stream()
                .map(uc -> COUPON_REMIND_KEY + uc.getId())
                .collect(Collectors.toList());
        redisTemplate.delete(keys);
    }

    private void saveUserCoupon(Coupon coupon, Long userId) {
        // 1.基本信息
        UserCoupon uc = new UserCoupon();
        uc.setUserId(userId);
        uc.setCouponId(coupon.getId());
        // 2.有效期信息：有效天数 > 0 表示从领取时间开始计算；否则使用模板指定的固定有效期
        Integer termDays = coupon.getTermDays();
        if (termDays != null && termDays > 0) {
            LocalDateTime now = LocalDateTime.now();
            uc.setTermBeginTime(now);
            uc.setTermEndTime(now.plusDays(termDays));
        } else {
            if (coupon.getTermEndTime() == null) {
                throw new BizIllegalException("优惠券有效期配置错误");
            }
            uc.setTermBeginTime(coupon.getTermBeginTime());
            uc.setTermEndTime(coupon.getTermEndTime());
        }
        // 3.保存
        save(uc);
    }

    @Override
    @Transactional
    public void writeOffCoupon(List<Long> userCouponIds) {
        // 0.健壮性校验
        if (CollUtils.isEmpty(userCouponIds)) {
            return;
        }
        // 1.查询优惠券
        List<UserCoupon> userCoupons = listByIds(userCouponIds);
        if (CollUtils.isEmpty(userCoupons)) {
            return;
        }
        // 2.过滤出可以核销的券：未使用，且在有效期内
        LocalDateTime now = LocalDateTime.now();
        List<UserCoupon> validCoupons = userCoupons.stream()
                .filter(coupon -> coupon != null
                        && UserCouponStatus.UNUSED == coupon.getStatus()
                        && coupon.getTermBeginTime() != null
                        && coupon.getTermEndTime() != null
                        && !now.isBefore(coupon.getTermBeginTime())
                        && !now.isAfter(coupon.getTermEndTime()))
                .collect(Collectors.toList());
        if (CollUtils.isEmpty(validCoupons)) {
            return;
        }
        // 3.组织要更新的数据
        List<UserCoupon> list = validCoupons.stream()
                .map(coupon -> {
                    UserCoupon c = new UserCoupon();
                    c.setId(coupon.getId());
                    c.setStatus(UserCouponStatus.USED);
                    return c;
                })
                .collect(Collectors.toList());
        // 4.核销，修改优惠券状态
        boolean success = updateBatchById(list);
        if (!success) {
            throw new DbException("核销优惠券失败！");
        }
        // 5.更新已使用数量（只统计真正核销成功的券）
        List<Long> couponIds = validCoupons.stream()
                .map(UserCoupon::getCouponId)
                .collect(Collectors.toList());
        int c = couponMapper.incrUsedNum(couponIds, 1);
        if (c < 1) {
            throw new DbException("更新优惠券使用数量失败！");
        }
    }

    @Override
    @Transactional
    public void refundCoupon(List<Long> userCouponIds) {
        // 0.健壮性校验
        if (CollUtils.isEmpty(userCouponIds)) {
            return;
        }
        // 1.查询优惠券
        List<UserCoupon> userCoupons = listByIds(userCouponIds);
        if (CollUtils.isEmpty(userCoupons)) {
            return;
        }
        // 2.过滤出已使用、可以退还的券
        List<UserCoupon> validCoupons = userCoupons.stream()
                .filter(coupon -> coupon != null && UserCouponStatus.USED == coupon.getStatus())
                .collect(Collectors.toList());
        if (CollUtils.isEmpty(validCoupons)) {
            return;
        }
        // 3.组织要更新的数据：未过期恢复为未使用，已过期改为已过期
        LocalDateTime now = LocalDateTime.now();
        List<UserCoupon> list = validCoupons.stream()
                .map(coupon -> {
                    UserCoupon c = new UserCoupon();
                    c.setId(coupon.getId());
                    UserCouponStatus status = coupon.getTermEndTime() != null && now.isAfter(coupon.getTermEndTime())
                            ? UserCouponStatus.EXPIRED : UserCouponStatus.UNUSED;
                    c.setStatus(status);
                    return c;
                })
                .collect(Collectors.toList());
        // 4.修改优惠券状态
        boolean success = updateBatchById(list);
        if (!success) {
            throw new DbException("退还优惠券失败！");
        }
        // 5.更新已使用数量（只统计真正退还的券）
        List<Long> couponIds = validCoupons.stream()
                .map(UserCoupon::getCouponId)
                .collect(Collectors.toList());
        int c = couponMapper.incrUsedNum(couponIds, -1);
        if (c < 1) {
            throw new DbException("更新优惠券使用数量失败！");
        }
    }

    @Override
    public List<String> queryDiscountRules(List<Long> userCouponIds) {
        // 1.查询优惠券信息
        List<Coupon> coupons = baseMapper.queryCouponByUserCouponIds(userCouponIds, UserCouponStatus.USED);
        if (CollUtils.isEmpty(coupons)) {
            return CollUtils.emptyList();
        }
        // 2.转换规则
        return coupons.stream()
                .map(c -> DiscountStrategy.getDiscount(c.getDiscountType()).getRule(c))
                .collect(Collectors.toList());
    }

}