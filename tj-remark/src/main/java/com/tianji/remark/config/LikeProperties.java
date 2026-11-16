package com.tianji.remark.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 点赞业务相关配置，支持Nacos动态刷新
 */
@Data
@Component
@ConfigurationProperties(prefix = "tj.like")
public class LikeProperties {

    /**
     * 需要处理点赞数据持久化的业务类型，例如：QA、NOTE
     */
    private List<String> bizTypes = new ArrayList<>();

    /**
     * 定时任务一次最多处理的业务数量
     */
    private Integer maxBizSize = 30;
}