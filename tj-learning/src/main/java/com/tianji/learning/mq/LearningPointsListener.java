package com.tianji.learning.mq;

import com.tianji.common.constants.MqConstants;
import com.tianji.learning.enums.PointsRecordType;
import com.tianji.learning.mq.message.SignInMessage;
import com.tianji.learning.service.IPointsRecordService;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.core.ExchangeTypes;
import org.springframework.amqp.rabbit.annotation.Exchange;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.QueueBinding;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * 学习积分相关的消息监听器
 */
@Component
@RequiredArgsConstructor
public class LearningPointsListener {

    private final IPointsRecordService recordService;

    /**
     * 监听新增回答事件：回答一次得5积分，每日上限20积分
     */
    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(name = "qa.points.queue", durable = "true"),
            exchange = @Exchange(name = MqConstants.Exchange.LEARNING_EXCHANGE, type = ExchangeTypes.TOPIC),
            key = MqConstants.Key.WRITE_REPLY
    ))
    public void listenWriteReplyMessage(Long userId) {
        recordService.addPointsRecord(userId, 5, PointsRecordType.QA);
    }

    /**
     * 监听新增评论事件：评论一次得2积分
     */
    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(name = "comment.points.queue", durable = "true"),
            exchange = @Exchange(name = MqConstants.Exchange.LEARNING_EXCHANGE, type = ExchangeTypes.TOPIC),
            key = MqConstants.Key.WRITE_COMMENT
    ))
    public void listenWriteCommentMessage(Long userId) {
        recordService.addPointsRecord(userId, 2, PointsRecordType.COMMENT);
    }

    /**
     * 监听学习完成事件：完成一节视频或考试得10积分，每日上限50积分
     */
    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(name = "learning.points.queue", durable = "true"),
            exchange = @Exchange(name = MqConstants.Exchange.LEARNING_EXCHANGE, type = ExchangeTypes.TOPIC),
            key = MqConstants.Key.LEARN_SECTION
    ))
    public void listenLearnSectionMessage(Long userId) {
        recordService.addPointsRecord(userId, 10, PointsRecordType.LEARNING);
    }

    /**
     * 监听新增笔记事件：写一条笔记得3积分，笔记类积分每日上限20积分
     */
    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(name = "note.new.points.queue", durable = "true"),
            exchange = @Exchange(name = MqConstants.Exchange.LEARNING_EXCHANGE, type = ExchangeTypes.TOPIC),
            key = MqConstants.Key.WRITE_NOTE
    ))
    public void listenWriteNoteMessage(Long userId) {
        recordService.addPointsRecord(userId, 3, PointsRecordType.NOTE);
    }

    /**
     * 监听笔记被采集事件：笔记被采集一次得2积分，笔记类积分每日上限20积分
     */
    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(name = "note.gathered.points.queue", durable = "true"),
            exchange = @Exchange(name = MqConstants.Exchange.LEARNING_EXCHANGE, type = ExchangeTypes.TOPIC),
            key = MqConstants.Key.NOTE_GATHERED
    ))
    public void listenNoteGatheredMessage(Long userId) {
        recordService.addPointsRecord(userId, 2, PointsRecordType.NOTE);
    }

    /**
     * 监听签到事件：签到得1积分，连续签到还有额外奖励
     */
    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(name = "sign.points.queue", durable = "true"),
            exchange = @Exchange(name = MqConstants.Exchange.LEARNING_EXCHANGE, type = ExchangeTypes.TOPIC),
            key = MqConstants.Key.SIGN_IN
    ))
    public void listenSignInMessage(SignInMessage message) {
        recordService.addPointsRecord(message.getUserId(), message.getPoints(), PointsRecordType.SIGN);
    }
}