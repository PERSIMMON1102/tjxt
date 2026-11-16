package com.tianji.remark.service;

import com.tianji.remark.domain.dto.LikeRecordFormDTO;
import com.tianji.remark.domain.po.LikedRecord;
import com.baomidou.mybatisplus.extension.service.IService;

import javax.validation.Valid;
import java.util.List;
import java.util.Set;

/**
 * <p>
 * 点赞记录表 服务类
 * </p>
 *
 * @author Tom
 * @since 2026-11-16
 */
public interface ILikedRecordService extends IService<LikedRecord> {

    void addLikeRecord(@Valid LikeRecordFormDTO recordDTO);

    Set<Long> isBizLiked(List<Long> bizIds);

    /**
     * 读取并移除缓存中的点赞数变更，通过MQ通知业务方
     *
     * @param bizType   业务类型
     * @param maxBizSize 一次最多处理的业务数量
     */
    void readLikedTimesAndSendMessage(String bizType, int maxBizSize);

    /**
     * 将Redis中缓存的点赞记录持久化到数据库
     *
     * @param bizType    业务类型
     * @param maxBizSize 一次最多处理的业务数量
     */
    void flushLikedRecords(String bizType, int maxBizSize);
}
