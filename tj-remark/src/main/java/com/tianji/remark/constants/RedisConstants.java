package com.tianji.remark.constants;

public interface RedisConstants {
    /**
     * 给业务点赞的用户集合的KEY前缀，后缀是业务id
     */
    String LIKES_BIZ_KEY_PREFIX = "likes:set:biz:";
    /**
     * 业务点赞数统计的KEY前缀，后缀是业务类型
     */
    String LIKES_TIMES_KEY_PREFIX = "likes:times:type:";
    /**
     * 待持久化的业务id集合的KEY前缀，后缀是业务类型
     */
    String LIKES_DIRTY_KEY_PREFIX = "likes:dirty:type:";
    /**
     * 业务点赞记录是否已加载到Redis的标记KEY前缀，后缀是业务id
     */
    String LIKES_LOADED_KEY_PREFIX = "likes:loaded:biz:";
}