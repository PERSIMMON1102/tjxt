package com.tianji.remark.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.tianji.api.dto.remark.LikedTimesDTO;
import com.tianji.common.autoconfigure.mq.RabbitMqHelper;
import com.tianji.common.utils.CollUtils;
import com.tianji.common.utils.StringUtils;
import com.tianji.common.utils.UserContext;
import com.tianji.remark.constants.RedisConstants;
import com.tianji.remark.domain.dto.LikeRecordFormDTO;
import com.tianji.remark.domain.po.LikedRecord;
import com.tianji.remark.mapper.LikedRecordMapper;
import com.tianji.remark.service.ILikedRecordService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.StringRedisConnection;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static com.tianji.common.constants.MqConstants.Exchange.LIKE_RECORD_EXCHANGE;
import static com.tianji.common.constants.MqConstants.Key.LIKED_TIMES_KEY_TEMPLATE;

/**
 * <p>
 * 点赞记录表 服务实现类（基于Redis合并写）
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LikedRecordServiceRedisImpl extends ServiceImpl<LikedRecordMapper, LikedRecord> implements ILikedRecordService {

    private final RabbitMqHelper mqHelper;
    private final StringRedisTemplate redisTemplate;

    @Override
    public void addLikeRecord(LikeRecordFormDTO recordDTO) {
        // 1.确保该业务的历史点赞记录已加载到Redis（防止Redis重启后丢失历史数据）
        ensureLoaded(recordDTO.getBizId());
        // 2.基于前端的参数，判断是执行点赞还是取消点赞
        boolean success = recordDTO.getLiked() ? like(recordDTO) : unlike(recordDTO);
        // 3.判断是否执行成功，如果失败，则直接结束
        if (!success) {
            return;
        }
        // 4.标记该业务有变更，等待定时任务持久化到数据库
        markDirty(recordDTO.getBizType(), recordDTO.getBizId());
        // 5.如果执行成功，统计点赞总数
        Long likedTimes = redisTemplate.opsForSet()
                .size(RedisConstants.LIKES_BIZ_KEY_PREFIX + recordDTO.getBizId());
        if (likedTimes == null) {
            return;
        }
        // 6.缓存点赞总数到Redis
        redisTemplate.opsForZSet().add(
                RedisConstants.LIKES_TIMES_KEY_PREFIX + recordDTO.getBizType(),
                recordDTO.getBizId().toString(),
                likedTimes
        );
    }

    private boolean unlike(LikeRecordFormDTO recordDTO) {
        // 1.获取用户id
        Long userId = UserContext.getUser();
        // 2.获取Key
        String key = RedisConstants.LIKES_BIZ_KEY_PREFIX + recordDTO.getBizId();
        // 3.执行SREM命令
        Long result = redisTemplate.opsForSet().remove(key, userId.toString());
        return result != null && result > 0;
    }

    private boolean like(LikeRecordFormDTO recordDTO) {
        // 1.获取用户id
        Long userId = UserContext.getUser();
        // 2.获取Key
        String key = RedisConstants.LIKES_BIZ_KEY_PREFIX + recordDTO.getBizId();
        // 3.执行SADD命令
        Long result = redisTemplate.opsForSet().add(key, userId.toString());
        return result != null && result > 0;
    }

    /**
     * 标记业务有变更，同时刷新加载标记，保证查询时以Redis为准
     */
    private void markDirty(String bizType, Long bizId) {
        redisTemplate.opsForSet().add(
                RedisConstants.LIKES_DIRTY_KEY_PREFIX + bizType, bizId.toString());
        redisTemplate.opsForValue().set(
                RedisConstants.LIKES_LOADED_KEY_PREFIX + bizId, "1");
    }

    @Override
    public Set<Long> isBizLiked(List<Long> bizIds) {
        if (CollUtils.isEmpty(bizIds)) {
            return Collections.emptySet();
        }
        // 1.获取登录用户id
        Long userId = UserContext.getUser();
        // 2.找出还没有加载到Redis的业务（既没有点赞集合，也没有加载标记）
        List<Long> missingBizIds = findMissingBizIds(bizIds);
        // 3.未加载的业务从数据库回源到Redis
        if (CollUtils.isNotEmpty(missingBizIds)) {
            loadFromDb(missingBizIds);
        }
        // 4.批量查询点赞状态，使用Pipeline减少网络交互次数
        List<Object> objects = redisTemplate.executePipelined((RedisCallback<Object>) connection -> {
            StringRedisConnection src = (StringRedisConnection) connection;
            for (Long bizId : bizIds) {
                String key = RedisConstants.LIKES_BIZ_KEY_PREFIX + bizId;
                src.sIsMember(key, userId.toString());
            }
            return null;
        });
        // 5.返回结果
        return IntStream.range(0, objects.size()) // 创建从0到集合size的流
                .filter(i -> (boolean) objects.get(i)) // 遍历每个元素，保留结果为true的角标i
                .mapToObj(bizIds::get)// 用角标i取bizIds中的对应数据，就是点赞过的id
                .collect(Collectors.toSet());// 收集
    }

    /**
     * 查询哪些业务尚未加载到Redis：点赞集合与加载标记都不存在
     */
    private List<Long> findMissingBizIds(List<Long> bizIds) {
        List<Object> flags = redisTemplate.executePipelined((RedisCallback<Object>) connection -> {
            StringRedisConnection src = (StringRedisConnection) connection;
            for (Long bizId : bizIds) {
                src.exists(RedisConstants.LIKES_BIZ_KEY_PREFIX + bizId);
                src.exists(RedisConstants.LIKES_LOADED_KEY_PREFIX + bizId);
            }
            return null;
        });
        List<Long> missing = new ArrayList<>();
        for (int i = 0; i < bizIds.size(); i++) {
            boolean setExists = (boolean) flags.get(i * 2);
            boolean loaded = (boolean) flags.get(i * 2 + 1);
            if (!setExists && !loaded) {
                missing.add(bizIds.get(i));
            }
        }
        return missing;
    }

    /**
     * 确保单个业务的历史点赞记录已加载到Redis，点赞/取消点赞前调用
     */
    private void ensureLoaded(Long bizId) {
        List<Object> flags = redisTemplate.executePipelined((RedisCallback<Object>) connection -> {
            StringRedisConnection src = (StringRedisConnection) connection;
            src.exists(RedisConstants.LIKES_BIZ_KEY_PREFIX + bizId);
            src.exists(RedisConstants.LIKES_LOADED_KEY_PREFIX + bizId);
            return null;
        });
        boolean setExists = (boolean) flags.get(0);
        boolean loaded = (boolean) flags.get(1);
        if (loaded) {
            return;
        }
        if (setExists) {
            // 集合存在但没有加载标记（历史数据），补上标记即可
            redisTemplate.opsForValue().set(
                    RedisConstants.LIKES_LOADED_KEY_PREFIX + bizId, "1");
            return;
        }
        loadFromDb(Collections.singletonList(bizId));
    }

    /**
     * 从数据库加载点赞记录到Redis，并标记为已加载
     */
    private void loadFromDb(List<Long> bizIds) {
        // 1.查询数据库中这些业务的点赞记录
        List<LikedRecord> records = list(new LambdaQueryWrapper<LikedRecord>()
                .in(LikedRecord::getBizId, bizIds));
        Map<Long, List<LikedRecord>> recordMap = records.stream()
                .collect(Collectors.groupingBy(LikedRecord::getBizId));
        // 2.写入Redis，并添加加载标记
        redisTemplate.executePipelined((RedisCallback<Object>) connection -> {
            StringRedisConnection src = (StringRedisConnection) connection;
            for (Long bizId : bizIds) {
                List<LikedRecord> list = recordMap.get(bizId);
                if (CollUtils.isNotEmpty(list)) {
                    String key = RedisConstants.LIKES_BIZ_KEY_PREFIX + bizId;
                    for (LikedRecord record : list) {
                        src.sAdd(key, record.getUserId().toString());
                    }
                }
                src.set(RedisConstants.LIKES_LOADED_KEY_PREFIX + bizId, "1");
            }
            return null;
        });
    }

    @Override
    public void readLikedTimesAndSendMessage(String bizType, int maxBizSize) {
        // 1.读取并移除Redis中缓存的点赞总数
        String key = RedisConstants.LIKES_TIMES_KEY_PREFIX + bizType;
        Set<ZSetOperations.TypedTuple<String>> tuples = redisTemplate.opsForZSet().popMin(key, maxBizSize);
        if (CollUtils.isEmpty(tuples)) {
            return;
        }
        // 2.数据转换
        List<LikedTimesDTO> list = new ArrayList<>(tuples.size());
        for (ZSetOperations.TypedTuple<String> tuple : tuples) {
            String bizId = tuple.getValue();
            Double likedTimes = tuple.getScore();
            if (bizId == null || likedTimes == null) {
                continue;
            }
            list.add(LikedTimesDTO.of(Long.valueOf(bizId), likedTimes.intValue()));
        }
        // 3.发送MQ消息
        mqHelper.send(
                LIKE_RECORD_EXCHANGE,
                StringUtils.format(LIKED_TIMES_KEY_TEMPLATE, bizType),
                list);
    }

    @Override
    public void flushLikedRecords(String bizType, int maxBizSize) {
        // 1.从待持久化集合中取出一批业务id（SPOP保证多实例下不会重复处理）
        String dirtyKey = RedisConstants.LIKES_DIRTY_KEY_PREFIX + bizType;
        List<String> bizIds = redisTemplate.opsForSet().pop(dirtyKey, maxBizSize);
        if (CollUtils.isEmpty(bizIds)) {
            return;
        }
        // 2.逐个业务持久化
        for (String bizIdStr : bizIds) {
            Long bizId = Long.valueOf(bizIdStr);
            try {
                flushOne(bizType, bizId);
            } catch (Exception e) {
                // 失败时放回待持久化集合，等待下一次重试
                log.error("持久化点赞记录失败，bizType={}, bizId={}", bizType, bizId, e);
                redisTemplate.opsForSet().add(dirtyKey, bizIdStr);
            }
        }
    }

    /**
     * 用Redis中的点赞快照覆盖数据库中该业务的点赞记录
     */
    private void flushOne(String bizType, Long bizId) {
        // 1.读取Redis中该业务当前的全部点赞用户
        Set<String> userIds = redisTemplate.opsForSet()
                .members(RedisConstants.LIKES_BIZ_KEY_PREFIX + bizId);
        // 2.删除数据库中该业务的旧记录
        remove(new LambdaQueryWrapper<LikedRecord>().eq(LikedRecord::getBizId, bizId));
        if (CollUtils.isEmpty(userIds)) {
            return;
        }
        // 3.批量写入最新快照
        List<LikedRecord> records = new ArrayList<>(userIds.size());
        for (String userId : userIds) {
            records.add(new LikedRecord()
                    .setBizId(bizId)
                    .setBizType(bizType)
                    .setUserId(Long.valueOf(userId)));
        }
        saveBatch(records);
    }
}