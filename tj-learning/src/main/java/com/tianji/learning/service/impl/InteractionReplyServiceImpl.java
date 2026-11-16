package com.tianji.learning.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tianji.api.client.remark.RemarkClient;
import com.tianji.api.client.user.UserClient;
import com.tianji.api.dto.user.UserDTO;
import com.tianji.common.domain.dto.PageDTO;
import com.tianji.common.exceptions.BadRequestException;
import com.tianji.common.utils.BeanUtils;
import com.tianji.common.utils.CollUtils;
import com.tianji.common.utils.UserContext;
import com.tianji.learning.domain.dto.ReplyDTO;
import com.tianji.learning.domain.po.InteractionQuestion;
import com.tianji.learning.domain.po.InteractionReply;
import com.tianji.learning.domain.query.ReplyPageQuery;
import com.tianji.learning.domain.vo.ReplyVO;
import com.tianji.learning.enums.QuestionStatus;
import com.tianji.learning.mapper.InteractionReplyMapper;
import com.tianji.learning.service.IInteractionQuestionService;
import com.tianji.learning.service.IInteractionReplyService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * <p>
 * 互动问题的回答或评论 服务实现类
 * </p>
 *
 * @author Tom
 * @since 2026-11-12
 */
@Service
@RequiredArgsConstructor
public class InteractionReplyServiceImpl extends ServiceImpl<InteractionReplyMapper, InteractionReply> implements IInteractionReplyService {

    private final IInteractionQuestionService questionService;
    private final UserClient userClient;
    private final RemarkClient remarkClient;

    @Override
    @Transactional
    public void saveReply(ReplyDTO replyDTO) {
        // 1.获取当前登录用户
        Long userId = UserContext.getUser();
        // 2.数据转换
        InteractionReply reply = BeanUtils.toBean(replyDTO, InteractionReply.class);
        reply.setUserId(userId);
        // 回答没有上级回答，数据库中统一用0表示（表结构answer_id默认值为0），避免查询时漏掉
        if (reply.getAnswerId() == null) {
            reply.setAnswerId(0L);
        }
        // 3.保存回答或评论
        save(reply);
        // 4.判断是回答还是评论，更新关联数据（answerId为null或0都表示回答）
        if (replyDTO.getAnswerId() == null || replyDTO.getAnswerId() == 0L) {
            // 4.1.是回答：记录问题的最新回答id，并累加回答数量
            questionService.lambdaUpdate()
                    .set(InteractionQuestion::getLatestAnswerId, reply.getId())
                    .setSql("answer_times = answer_times + 1")
                    .eq(InteractionQuestion::getId, replyDTO.getQuestionId())
                    .update();
        } else {
            // 4.2.是评论：累加所属回答的评论数量
            lambdaUpdate()
                    .setSql("reply_times = reply_times + 1")
                    .eq(InteractionReply::getId, replyDTO.getAnswerId())
                    .update();
        }
        // 5.学生提交的内容需要管理员重新查看，把问题标记为未查看
        if (Boolean.TRUE.equals(replyDTO.getIsStudent())) {
            questionService.lambdaUpdate()
                    .set(InteractionQuestion::getStatus, QuestionStatus.UN_CHECK)
                    .eq(InteractionQuestion::getId, replyDTO.getQuestionId())
                    .update();
        }
    }

    @Override
    public PageDTO<ReplyVO> queryReplyPage(ReplyPageQuery query, boolean isAdmin) {
        // 1.参数校验：问题id与回答id至少有一个
        Long questionId = query.getQuestionId();
        Long answerId = query.getAnswerId();
        if (questionId == null && answerId == null) {
            throw new BadRequestException("问题id和回答id不能都为空");
        }
        // 2.分页查询
        // 2.1.查询回答时，需要排除评论（评论的answer_id不为空）
        // 2.2.用户端不展示被隐藏的回答或评论
        // 回答的answer_id为0（表结构默认值），评论的answer_id为上级回答id
        Page<InteractionReply> page = lambdaQuery()
                .eq(questionId != null, InteractionReply::getQuestionId, questionId)
                .eq(answerId != null, InteractionReply::getAnswerId, answerId)
                .and(answerId == null, w -> w.eq(InteractionReply::getAnswerId, 0L)
                        .or().isNull(InteractionReply::getAnswerId))
                .eq(!isAdmin, InteractionReply::getHidden, false)
                .page(isAdmin
                        ? query.toMpPageDefaultSortByCreateTimeDesc()
                        : query.toMpPage("liked_times", false));
        List<InteractionReply> records = page.getRecords();
        if (CollUtils.isEmpty(records)) {
            return PageDTO.empty(page);
        }

        // 3.准备用户数据：回答者、被评论的目标用户
        Set<Long> userIds = new HashSet<>();
        Set<Long> answerIds = new HashSet<>();
        for (InteractionReply r : records) {
            if (isAnswer(r)) {
                // 当前记录是回答，需要统计其下的评论数量
                answerIds.add(r.getId());
            }
            // 用户端匿名的回答不查询用户信息，管理端无视匿名
            if (isAdmin || !Boolean.TRUE.equals(r.getAnonymity())) {
                userIds.add(r.getUserId());
            }
            if (r.getTargetUserId() != null) {
                userIds.add(r.getTargetUserId());
            }
        }
        userIds.remove(null);
        Map<Long, UserDTO> userMap = new HashMap<>(userIds.size());
        if (CollUtils.isNotEmpty(userIds)) {
            List<UserDTO> users = userClient.queryUserByIds(userIds);
            if (CollUtils.isNotEmpty(users)) {
                userMap = users.stream().collect(Collectors.toMap(UserDTO::getId, u -> u));
            }
        }
        // 4.统计回答下的评论数量：用户端不统计被隐藏的评论，管理端全部统计
        Map<Long, Integer> replyTimesMap = answerIds.isEmpty()
                ? Collections.emptyMap()
                : countReplyTimes(answerIds, isAdmin);

        // 5.查询当前用户对这些回答或评论的点赞状态，管理端无需查询
        Set<Long> likedIds = Collections.emptySet();
        if (!isAdmin) {
            Set<Long> bizIds = records.stream()
                    .map(InteractionReply::getId)
                    .collect(Collectors.toSet());
            likedIds = remarkClient.isBizLiked(bizIds);
        }

        // 6.封装VO
        List<ReplyVO> voList = new ArrayList<>(records.size());
        for (InteractionReply r : records) {
            ReplyVO vo = BeanUtils.copyBean(r, ReplyVO.class);
            // 6.1.回答下的评论数量
            vo.setReplyTimes(replyTimesMap.getOrDefault(r.getId(), 0));
            // 6.2.回答者信息
            if (!isAdmin && Boolean.TRUE.equals(r.getAnonymity())) {
                // 用户端匿名：不返回用户信息
                vo.setUserId(null);
                vo.setUserName(null);
                vo.setUserIcon(null);
            } else {
                UserDTO user = userMap.get(r.getUserId());
                if (user != null) {
                    vo.setUserName(user.getName());
                    vo.setUserIcon(user.getIcon());
                }
            }
            // 6.3.评论的目标用户昵称
            if (r.getTargetUserId() != null) {
                UserDTO target = userMap.get(r.getTargetUserId());
                if (target != null) {
                    vo.setTargetUserName(target.getName());
                }
            }
            // 6.4.当前用户是否点赞过
            vo.setLiked(likedIds.contains(r.getId()));
            // 6.5.隐藏字段仅管理端使用
            if (!isAdmin) {
                vo.setHidden(null);
            }
            voList.add(vo);
        }
        return PageDTO.of(page, voList);
    }

    @Override
    @Transactional
    public void updateReplyHidden(Long id, Boolean hidden) {
        // 1.查询回答或评论
        InteractionReply reply = getById(id);
        if (reply == null) {
            throw new BadRequestException("回答或评论不存在");
        }
        // 2.修改隐藏状态
        lambdaUpdate()
                .set(InteractionReply::getHidden, hidden)
                .eq(InteractionReply::getId, id)
                .update();
        // 3.隐藏的是回答时，其下的评论也要同步隐藏或显示
        if (isAnswer(reply)) {
            lambdaUpdate()
                    .set(InteractionReply::getHidden, hidden)
                    .eq(InteractionReply::getAnswerId, id)
                    .update();
        }
    }

    /**
     * 判断记录是否是回答：answer_id为0或null都表示回答
     */
    private boolean isAnswer(InteractionReply reply) {
        return reply.getAnswerId() == null || reply.getAnswerId() == 0L;
    }

    /**
     * 统计回答下的评论数量
     *
     * @param answerIds 回答id集合
     * @param isAdmin   管理端统计时包含被隐藏的评论
     * @return 回答id与评论数量的映射
     */
    private Map<Long, Integer> countReplyTimes(Set<Long> answerIds, boolean isAdmin) {
        QueryWrapper<InteractionReply> wrapper = new QueryWrapper<>();
        wrapper.select("answer_id", "COUNT(*) AS num")
                .in("answer_id", answerIds)
                .eq(!isAdmin, "hidden", false)
                .groupBy("answer_id");
        List<Map<String, Object>> counts = baseMapper.selectMaps(wrapper);
        Map<Long, Integer> map = new HashMap<>(counts.size());
        for (Map<String, Object> count : counts) {
            Object answerId = count.get("answer_id");
            Object num = count.get("num");
            if (answerId != null && num != null) {
                map.put(((Number) answerId).longValue(), ((Number) num).intValue());
            }
        }
        return map;
    }
}