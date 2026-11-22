-- 领券资格校验脚本
-- KEYS[1] = 优惠券缓存KEY：prs:coupon:{couponId}
-- KEYS[2] = 用户已领数量KEY：prs:user:coupon:{couponId}
-- ARGV[1] = 用户id
-- 返回：0-成功，1-优惠券不存在，2-库存不足，3-不在发放时间内，4-超出限领，5-兑换码券
if redis.call('exists', KEYS[1]) == 0 then
    return 1
end
local totalNum = tonumber(redis.call('hget', KEYS[1], 'totalNum'))
if totalNum == nil or totalNum <= 0 then
    return 2
end
local now = tonumber(redis.call('time')[1])
local beginTime = tonumber(redis.call('hget', KEYS[1], 'issueBeginTime'))
local endTime = tonumber(redis.call('hget', KEYS[1], 'issueEndTime'))
if beginTime == nil or endTime == nil or now < beginTime or now > endTime then
    return 3
end
if redis.call('hget', KEYS[1], 'obtainWay') ~= 'PUBLIC' then
    return 5
end
local userLimit = tonumber(redis.call('hget', KEYS[1], 'userLimit'))
local count = redis.call('hincrby', KEYS[2], ARGV[1], 1)
if userLimit == nil or count > userLimit then
    redis.call('hincrby', KEYS[2], ARGV[1], -1)
    return 4
end
redis.call('hincrby', KEYS[1], 'totalNum', -1)
return 0