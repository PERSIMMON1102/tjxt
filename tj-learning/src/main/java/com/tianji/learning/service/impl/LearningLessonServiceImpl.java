package com.tianji.learning.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tianji.api.client.course.CourseClient;
import com.tianji.api.dto.course.CourseSimpleInfoDTO;
import com.tianji.common.domain.dto.PageDTO;
import com.tianji.common.domain.query.PageQuery;
import com.tianji.common.exceptions.BadRequestException;
import com.tianji.common.utils.AssertUtils;
import com.tianji.common.utils.BeanUtils;
import com.tianji.common.utils.CollUtils;
import com.tianji.common.utils.UserContext;
import com.tianji.learning.domain.po.LearningLesson;
import com.tianji.learning.domain.vo.LearningLessonVO;
import com.tianji.learning.enums.LessonStatus;
import com.tianji.learning.mapper.LearningLessonMapper;
import com.tianji.learning.service.ILearningLessonService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * <p>
 * 学生课程表 服务实现类
 * </p>
 *
 * @author Tom
 * @since 2026-11-01
 */
@Service
@RequiredArgsConstructor
public class LearningLessonServiceImpl extends ServiceImpl<LearningLessonMapper, LearningLesson> implements ILearningLessonService {

    private final CourseClient courseClient;

    @Override
    @Transactional
    public void addUserLessons(Long userId, List<Long> courseIds) {
        //1.查询课程有效期
        List<CourseSimpleInfoDTO> cInfoList = courseClient.getSimpleInfoList(courseIds);
        if (cInfoList == null || cInfoList.isEmpty()) {
            //课程不存在，无法添加
            log.error("课程信息不存在，无法添加到课表");
            return;
        }
        //2.循环遍历，处理LearningLesson数据
        List<LearningLesson> list = new ArrayList<>(cInfoList.size());
        for (CourseSimpleInfoDTO cInfo : cInfoList) {
            LearningLesson lesson = new LearningLesson();
            //2.1获取过期时间
            Integer validDuration = cInfo.getValidDuration();
            if (validDuration != null && validDuration > 0) {
                LocalDateTime now = LocalDateTime.now();
                lesson.setCreateTime(now);
                lesson.setExpireTime(now.plusDays(validDuration));
            }
            //2.2填充userId和courseId
            lesson.setUserId(userId);
            lesson.setCourseId(cInfo.getId());
            list.add(lesson);
        }
        //3.批量新增
        saveBatch(list);
    }

    @Override
    @Transactional
    public void removeUserLessons(Long userId, List<Long> courseIds) {
        //1.健壮性校验
        if (userId == null || CollUtils.isEmpty(courseIds)) {
            return;
        }
        //2.删除用户课表中的指定课程
        remove(new LambdaQueryWrapper<LearningLesson>()
                .eq(LearningLesson::getUserId, userId)
                .in(LearningLesson::getCourseId, courseIds));
    }

    @Override
    public PageDTO<LearningLessonVO> queryMyLessons(PageQuery query) {
        //1.获取当前用户
        Long userId = UserContext.getUser();
        //2.分页查询
        Page<LearningLesson> page=lambdaQuery()
                .eq(LearningLesson::getUserId, userId)
                .page(query.toMpPage("latest_learn_time",false));
        List<LearningLesson>records = page.getRecords();
        if(CollUtils.isEmpty(records)){
            return PageDTO.empty(page);
        }
        //3.查询课程信息
        //3.1 获取课程id
        Set<Long> cIds = records.stream().map(LearningLesson::getCourseId).collect(Collectors.toSet());
        //3.2 查询课程信息
        List<CourseSimpleInfoDTO> cInfoList = courseClient.getSimpleInfoList(cIds);
        if (CollUtils.isEmpty(cInfoList)) {
            throw new BadRequestException("课程信息不存在");
        }
        //3.3 把课程集合处理成Map,key是courseId,值是course本身
        Map<Long, CourseSimpleInfoDTO> cMap = cInfoList.stream()
                .collect(Collectors.toMap(CourseSimpleInfoDTO::getId, c -> c));
        //4.分装VO返回
        List<LearningLessonVO>list = new ArrayList<>(records.size());
        //4.1循环遍历，把LearningLesson转为VO
        for(LearningLesson r:records){
            //4.2拷贝基础属性到VO
            LearningLessonVO vo = BeanUtils.copyBean(r,LearningLessonVO.class);
            //4.3 获取课程信息，填充到VO
             CourseSimpleInfoDTO cInfo= cMap.get(r.getCourseId());
             vo.setCourseName(cInfo.getName());
             vo.setCourseCoverUrl(cInfo.getCoverUrl());
             vo.setSections(cInfo.getSectionNum());
             list.add(vo);
        }
        return PageDTO.of(page,list);
    }

    @Override
    public void deleteLesson(Long courseId) {
        //1.获取当前用户
        Long userId = UserContext.getUser();
        //2.查询用户课表中的课程
        LearningLesson lesson = lambdaQuery()
                .eq(LearningLesson::getUserId, userId)
                .eq(LearningLesson::getCourseId, courseId)
                .one();
        AssertUtils.isNotNull(lesson, "课表中不存在该课程");
        //3.只有已失效的课程才允许删除
        if (!isLessonExpired(lesson)) {
            throw new BadRequestException("课程未失效，不能删除");
        }
        //4.删除课表记录
        removeById(lesson.getId());
    }

    @Override
    public Long isLessonValid(Long courseId) {
        //1.获取当前用户
        Long userId = UserContext.getUser();
        //2.查询用户课表中的课程
        LearningLesson lesson = lambdaQuery()
                .eq(LearningLesson::getUserId, userId)
                .eq(LearningLesson::getCourseId, courseId)
                .one();
        //3.课表中没有该课程，说明没有报名
        if (lesson == null) {
            return null;
        }
        //4.课程已失效，没有学习资格
        if (isLessonExpired(lesson)) {
            return null;
        }
        //5.返回课表id
        return lesson.getId();
    }

    @Override
    public LearningLessonVO queryLessonByCourseId(Long courseId) {
        //1.获取当前用户
        Long userId = UserContext.getUser();
        //2.查询用户课表中的课程
        LearningLesson lesson = lambdaQuery()
                .eq(LearningLesson::getUserId, userId)
                .eq(LearningLesson::getCourseId, courseId)
                .one();
        //3.转换为VO返回
        return BeanUtils.copyBean(lesson, LearningLessonVO.class);
    }

    @Override
    public Integer countLearningLessonByCourse(Long courseId) {
        return lambdaQuery().eq(LearningLesson::getCourseId, courseId).count();
    }

    /**
     * 判断课程是否已失效：状态为已失效，或者已经超过有效期
     */
    private boolean isLessonExpired(LearningLesson lesson) {
        if (LessonStatus.EXPIRED.equals(lesson.getStatus())) {
            return true;
        }
        return lesson.getExpireTime() != null && lesson.getExpireTime().isBefore(LocalDateTime.now());
    }
}
