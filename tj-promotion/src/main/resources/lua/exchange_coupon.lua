-- 兑换码兑换资格校验脚本
-- KEYS[1] = 兑换码已兑换标记 bitmap：coupon:code:map
-- KEYS[2] = 兑换码范围 ZSet：coupon:code:range，member=优惠券id，score=该券兑换码最大序列号
-- ARGV[1] = 兑换码序列号
-- ARGV[2] = 序列号 + 5000（单张券最多5000个兑换码）
-- ARGV[3] = 用户id
-- 返回：1-兑换码已兑换，2-兑换码不存在，3-优惠券不存在，4-不在发放时间内，5-超出限领；成功返回优惠券id
if redis.call('getbit', KEYS[1], ARGV[1]) == 1 then
    return "1"
end
local arr = redis.call('zrangebyscore', KEYS[2], ARGV[1], ARGV[2], 'LIMIT', 0, 1)
if #arr == 0 then
    return "2"
end
local couponId = arr[1]
local couponKey = "prs:coupon:" .. couponId
local userKey = "prs:user:coupon:" .. couponId
if redis.call('exists', couponKey) == 0 then
    return "3"
end
local now = tonumber(redis.call('time')[1])
local beginTime = tonumber(redis.call('hget', couponKey, 'issueBeginTime'))
local endTime = tonumber(redis.call('hget', couponKey, 'issueEndTime'))
if beginTime == nil or endTime == nil or now < beginTime or now > endTime then
    return "4"
end
local userLimit = tonumber(redis.call('hget', couponKey, 'userLimit'))
local count = redis.call('hincrby', userKey, ARGV[3], 1)
if userLimit == nil or count > userLimit then
    redis.call('hincrby', userKey, ARGV[3], -1)
    return "5"
end
redis.call('setbit', KEYS[1], ARGV[1], "1")
return couponId