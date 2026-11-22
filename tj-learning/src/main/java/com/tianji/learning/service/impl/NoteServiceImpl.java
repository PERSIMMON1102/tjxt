package com.tianji.learning.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.OrderItem;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.tianji.api.cache.CategoryCache;
import com.tianji.api.client.course.CatalogueClient;
import com.tianji.api.client.course.CourseClient;
import com.tianji.api.client.search.SearchClient;
import com.tianji.api.client.user.UserClient;
import com.tianji.api.dto.course.CataSimpleInfoDTO;
import com.tianji.api.dto.course.CourseFullInfoDTO;
import com.tianji.api.dto.course.CourseSimpleInfoDTO;
import com.tianji.api.dto.user.UserDTO;
import com.tianji.common.autoconfigure.mq.RabbitMqHelper;
import com.tianji.common.constants.Constant;
import com.tianji.common.constants.MqConstants;
import com.tianji.common.domain.dto.PageDTO;
import com.tianji.common.exceptions.BadRequestException;
import com.tianji.common.utils.AssertUtils;
import com.tianji.common.utils.BeanUtils;
import com.tianji.common.utils.BooleanUtils;
import com.tianji.common.utils.CollUtils;
import com.tianji.common.utils.StringUtils;
import com.tianji.common.utils.UserContext;
import com.tianji.learning.domain.dto.NoteFormDTO;
import com.tianji.learning.domain.po.Note;
import com.tianji.learning.domain.query.NoteAdminPageQuery;
import com.tianji.learning.domain.query.NotePageQuery;
import com.tianji.learning.domain.vo.NoteAdminDetailVO;
import com.tianji.learning.domain.vo.NoteAdminVO;
import com.tianji.learning.domain.vo.NoteVO;
import com.tianji.learning.mapper.NoteMapper;
import com.tianji.learning.service.INoteService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * <p>
 * 学习笔记 服务实现类
 * </p>
 *
 * @author Tom
 */
@Service
@RequiredArgsConstructor
public class NoteServiceImpl extends ServiceImpl<NoteMapper, Note> implements INoteService {

    private final CourseClient courseClient;
    private final SearchClient searchClient;
    private final CatalogueClient catalogueClient;
    private final UserClient userClient;
    private final RabbitMqHelper mqHelper;
    private final CategoryCache categoryCache;

    @Override
    @Transactional
    public void saveNote(NoteFormDTO noteDTO) {
        Long userId = UserContext.getUser();
        // 1.数据转换
        Note note = BeanUtils.toBean(noteDTO, Note.class);
        // 2.填充用户信息
        note.setUserId(userId);
        note.setAuthorId(userId);
        // 3.保存笔记
        save(note);
        // 4.发送MQ消息，异步累加写笔记积分
        mqHelper.send(
                MqConstants.Exchange.LEARNING_EXCHANGE,
                MqConstants.Key.WRITE_NOTE,
                userId);
    }

    @Override
    @Transactional
    public void gatherNote(Long id) {
        // 1.获取用户
        Long userId = UserContext.getUser();
        // 2.判断笔记是否存在，且允许被采集
        Note note = getById(id);
        if (note == null || BooleanUtils.isTrue(note.getIsPrivate()) || BooleanUtils.isTrue(note.getHidden())) {
            throw new BadRequestException("笔记不存在");
        }
        // 2.1.判断是否已经采集过该笔记
        long gatheredCount = lambdaQuery()
                .eq(Note::getUserId, userId)
                .eq(Note::getGatheredNoteId, id)
                .count();
        if (gatheredCount > 0) {
            throw new BadRequestException("已经采集过该笔记");
        }
        // 3.另存一份采集笔记，作者信息保持不变
        Long authorId = note.getAuthorId();
        note.setGatheredNoteId(note.getId());
        note.setIsGathered(true);
        note.setIsPrivate(true);
        note.setUserId(userId);
        note.setId(null);
        // 采集时间以当前时间为准，交给数据库默认值填充
        note.setCreateTime(null);
        note.setUpdateTime(null);
        save(note);
        // 4.发送MQ消息，异步累加“笔记被采集”积分，积分给笔记作者
        if (authorId != null && !authorId.equals(userId)) {
            mqHelper.send(
                    MqConstants.Exchange.LEARNING_EXCHANGE,
                    MqConstants.Key.NOTE_GATHERED,
                    authorId);
        }
    }

    @Override
    public void removeGatherNote(Long id) {
        // 1.笔记删除条件：当前用户采集的、被采集笔记id为id的记录
        LambdaUpdateWrapper<Note> wrapper =
                Wrappers.lambdaUpdate(Note.class)
                        .eq(Note::getUserId, UserContext.getUser())
                        .eq(Note::getGatheredNoteId, id);
        // 2.删除采集笔记
        baseMapper.delete(wrapper);
    }

    @Override
    public void updateNote(NoteFormDTO noteDTO) {
        // 1.获取用户
        Long userId = UserContext.getUser();
        // 2.查询笔记并校验
        Note note = getById(noteDTO.getId());
        AssertUtils.isNotNull(note, "笔记不存在");
        AssertUtils.equals(note.getUserId(), userId, "笔记不存在");
        // 3.数据封装
        Note n = new Note();
        n.setId(noteDTO.getId());
        // 采集笔记不能设置为公开
        if (noteDTO.getIsPrivate() != null) {
            n.setIsPrivate(BooleanUtils.isTrue(note.getIsGathered()) || noteDTO.getIsPrivate());
        }
        n.setContent(noteDTO.getContent());
        updateById(n);
    }

    @Override
    public PageDTO<NoteVO> queryNotePage(NotePageQuery query) {
        // 1.条件判断
        Long courseId = query.getCourseId();
        Long sectionId = query.getSectionId();
        if (courseId == null && sectionId == null) {
            throw new BadRequestException("课程或小节不能为空");
        }
        // 2.分页条件
        Page<Note> p = new Page<>(query.getPageNo(), query.getPageSize());
        // 3.获取用户
        Long userId = UserContext.getUser();
        // 4.搜索：只查询我的笔记，或者查询指定课程/小节下所有公开笔记
        Page<Note> page;
        if (BooleanUtils.isTrue(query.getOnlyMine())) {
            page = lambdaQuery()
                    .eq(Note::getUserId, userId)
                    .eq(Note::getHidden, false)
                    .eq(courseId != null, Note::getCourseId, courseId)
                    .eq(sectionId != null, Note::getSectionId, sectionId)
                    .orderByAsc(sectionId != null, Note::getNoteMoment)
                    .orderByDesc(sectionId == null, Note::getId)
                    .page(p);
        } else {
            page = baseMapper.queryNotePageBySectionId(p, userId, courseId, sectionId);
        }
        // 5.数据处理
        return parseNotePages(page);
    }

    @Override
    public PageDTO<NoteAdminVO> queryNotePageForAdmin(NoteAdminPageQuery query) {
        // 1.分页条件
        Page<Note> notePage = new Page<>(query.getPageNo(), query.getPageSize());
        // 2.根据课程名称搜索课程id
        List<Long> courseIdList = null;
        if (StringUtils.isNotEmpty(query.getName())) {
            courseIdList = searchClient.queryCoursesIdByName(query.getName());
            if (CollUtils.isEmpty(courseIdList)) {
                return PageDTO.empty(notePage);
            }
        }
        // 3.排序条件
        if (StringUtils.isNotBlank(query.getSortBy())) {
            notePage.addOrder(new OrderItem(query.getSortBy(), query.getIsAsc()));
        } else {
            notePage.addOrder(new OrderItem(Constant.DATA_FIELD_NAME_CREATE_TIME, false));
        }
        // 4.搜索条件
        LocalDateTime beginTime = query.getBeginTime();
        LocalDateTime endTime = query.getEndTime();
        QueryWrapper<Note> wrapper = new QueryWrapper<>();
        wrapper.lambda()
                .in(CollUtils.isNotEmpty(courseIdList), Note::getCourseId, courseIdList)
                .eq(query.getHidden() != null, Note::getHidden, query.getHidden())
                .ge(beginTime != null, Note::getCreateTime, beginTime)
                .le(endTime != null, Note::getCreateTime, endTime);
        // 5.查询
        Page<Note> page = baseMapper.queryNotePage(notePage, wrapper);
        List<Note> records = page.getRecords();
        if (CollUtils.isEmpty(records)) {
            return PageDTO.empty(page);
        }
        // 6.收集关联id
        Set<Long> courseIds = new HashSet<>();
        Set<Long> csIds = new HashSet<>();
        Set<Long> uIds = new HashSet<>();
        for (Note r : records) {
            courseIds.add(r.getCourseId());
            if (r.getChapterId() != null) {
                csIds.add(r.getChapterId());
            }
            if (r.getSectionId() != null) {
                csIds.add(r.getSectionId());
            }
            uIds.add(r.getUserId());
        }
        // 7.查询课程、章节、用户信息
        List<CourseSimpleInfoDTO> courseInfos = courseClient.getSimpleInfoList(courseIds);
        Map<Long, String> courseMap = CollUtils.isEmpty(courseInfos) ?
                new HashMap<>() :
                courseInfos.stream().collect(Collectors.toMap(CourseSimpleInfoDTO::getId, CourseSimpleInfoDTO::getName));
        List<CataSimpleInfoDTO> csInfos = catalogueClient.batchQueryCatalogue(csIds);
        Map<Long, String> csNameMap = CollUtils.isEmpty(csInfos) ?
                new HashMap<>() :
                csInfos.stream().collect(Collectors.toMap(CataSimpleInfoDTO::getId, CataSimpleInfoDTO::getName));
        List<UserDTO> userInfos = userClient.queryUserByIds(uIds);
        Map<Long, String> userMap = CollUtils.isEmpty(userInfos) ?
                new HashMap<>() :
                userInfos.stream().collect(Collectors.toMap(UserDTO::getId, UserDTO::getName));
        // 8.封装VO
        List<NoteAdminVO> list = new ArrayList<>(records.size());
        for (Note r : records) {
            NoteAdminVO v = BeanUtils.toBean(r, NoteAdminVO.class);
            v.setAuthorName(userMap.get(r.getUserId()));
            v.setCourseName(courseMap.get(r.getCourseId()));
            v.setChapterName(csNameMap.get(r.getChapterId()));
            v.setSectionName(csNameMap.get(r.getSectionId()));
            v.setUsedTimes(r.getUsedTimes());
            list.add(v);
        }
        return new PageDTO<>(page.getTotal(), page.getPages(), list);
    }

    @Override
    public NoteAdminDetailVO queryNoteDetailForAdmin(Long id) {
        // 1.查询笔记
        Note note = getById(id);
        AssertUtils.isNotNull(note, "笔记不存在");
        // 2.转换VO
        NoteAdminDetailVO vo = BeanUtils.toBean(note, NoteAdminDetailVO.class);
        // 3.查询课程信息
        CourseFullInfoDTO courseInfo =
                courseClient.getCourseInfoById(note.getCourseId(), false, false);
        if (courseInfo != null) {
            vo.setCourseName(courseInfo.getName());
            List<Long> cateIds = List.of(
                    courseInfo.getFirstCateId(),
                    courseInfo.getSecondCateId(),
                    courseInfo.getThirdCateId());
            vo.setCategoryNames(categoryCache.getCategoryNames(cateIds));
        }
        // 4.查询章节信息
        if (note.getChapterId() != null && note.getSectionId() != null) {
            List<CataSimpleInfoDTO> cataInfos = catalogueClient
                    .batchQueryCatalogue(List.of(note.getChapterId(), note.getSectionId()));
            if (cataInfos != null && cataInfos.size() == 2) {
                for (CataSimpleInfoDTO cataInfo : cataInfos) {
                    if (cataInfo.getId().equals(note.getChapterId())) {
                        vo.setChapterName(cataInfo.getName());
                    } else {
                        vo.setSectionName(cataInfo.getName());
                    }
                }
            }
        }
        // 5.查询用户信息
        // 5.1.查询采集过当前笔记的用户
        Set<Long> gatherIds = baseMapper.queryNoteGathers(id);
        vo.setUsedTimes(gatherIds == null ? 0 : gatherIds.size());
        // 5.2.当前笔记作者
        Set<Long> uIds = gatherIds == null ? new HashSet<>() : new HashSet<>(gatherIds);
        Long authorId = note.getAuthorId();
        if (authorId != null && authorId != 0L) {
            uIds.add(authorId);
        }
        uIds.remove(0L);
        if (CollUtils.isEmpty(uIds)) {
            return vo;
        }
        List<UserDTO> users = userClient.queryUserByIds(uIds);
        if (CollUtils.isEmpty(users)) {
            return vo;
        }
        // 5.3.填充作者信息和采集者信息
        List<String> gathers = new ArrayList<>();
        for (UserDTO user : users) {
            if (user.getId().equals(authorId)) {
                vo.setAuthorName(user.getName());
                vo.setAuthorPhone(user.getCellPhone());
            } else {
                gathers.add(user.getName());
            }
        }
        vo.setGathers(gathers);
        return vo;
    }

    @Override
    public void hiddenNote(Long id, boolean hidden) {
        Note note = new Note();
        note.setId(id);
        note.setHidden(hidden);
        updateById(note);
    }

    @Override
    public void removeMyNote(Long id) {
        // 1.获取用户
        Long userId = UserContext.getUser();
        // 2.查询笔记并校验
        Note note = getById(id);
        AssertUtils.isNotNull(note, "笔记不存在");
        AssertUtils.equals(userId, note.getUserId(), "无权删除他人笔记");
        // 3.删除笔记
        removeById(id);
    }

    private PageDTO<NoteVO> parseNotePages(Page<Note> page) {
        // 1.非空判断
        List<Note> records = page.getRecords();
        if (CollUtils.isEmpty(records)) {
            return PageDTO.empty(page);
        }
        // 2.查询笔记作者
        Set<Long> userIds = records.stream().map(Note::getAuthorId).collect(Collectors.toSet());
        List<UserDTO> stuInfos = userClient.queryUserByIds(userIds);
        Map<Long, UserDTO> sMap = CollUtils.isEmpty(stuInfos) ?
                new HashMap<>() :
                stuInfos.stream().collect(Collectors.toMap(UserDTO::getId, s -> s));
        // 3.处理VO
        List<NoteVO> list = new ArrayList<>(records.size());
        for (Note r : records) {
            NoteVO v = BeanUtils.toBean(r, NoteVO.class);
            UserDTO author = sMap.get(r.getAuthorId());
            if (author != null) {
                v.setAuthorId(author.getId());
                v.setAuthorName(author.getName());
                v.setAuthorIcon(author.getIcon());
            }
            v.setIsGathered(BooleanUtils.isTrue(r.getIsGathered()));
            list.add(v);
        }
        return new PageDTO<>(page.getTotal(), page.getPages(), list);
    }
}