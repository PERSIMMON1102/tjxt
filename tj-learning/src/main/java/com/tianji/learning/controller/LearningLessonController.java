package com.tianji.learning.controller;


import com.tianji.common.domain.dto.PageDTO;
import com.tianji.common.domain.query.PageQuery;
import com.tianji.learning.domain.vo.LearningLessonVO;
import com.tianji.learning.service.ILearningLessonService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiImplicitParam;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

import org.springframework.web.bind.annotation.RestController;

/**
 * <p>
 * 学生课程表 前端控制器
 * </p>
 *
 * @author Tom
 * @since 2026-11-01
 */
@RestController
@RequestMapping("/lessons")
@Api(tags = "我的课表相关接口")
@RequiredArgsConstructor
public class LearningLessonController {

    private final ILearningLessonService lessonService;

    @GetMapping("/page")
    @ApiOperation("分页查询我的课表")
    public PageDTO<LearningLessonVO> queryMyLessons(PageQuery query) {
        return lessonService.queryMyLessons(query);
    }

    @DeleteMapping("/{courseId:\\d+}")
    @ApiOperation("删除课表中已失效的课程")
    @ApiImplicitParam(name = "courseId", value = "课程id", required = true, dataTypeClass = Long.class)
    public void deleteLesson(@PathVariable("courseId") Long courseId) {
        lessonService.deleteLesson(courseId);
    }

    @GetMapping("/{courseId:\\d+}/valid")
    @ApiOperation("校验当前用户是否可以学习当前课程")
    @ApiImplicitParam(name = "courseId", value = "课程id", required = true, dataTypeClass = Long.class)
    public Long isLessonValid(@PathVariable("courseId") Long courseId) {
        return lessonService.isLessonValid(courseId);
    }

    @GetMapping("/{courseId:\\d+}")
    @ApiOperation("查询用户课表中指定课程状态")
    @ApiImplicitParam(name = "courseId", value = "课程id", required = true, dataTypeClass = Long.class)
    public LearningLessonVO queryLessonByCourseId(@PathVariable("courseId") Long courseId) {
        return lessonService.queryLessonByCourseId(courseId);
    }

    @GetMapping("/{courseId:\\d+}/count")
    @ApiOperation("统计课程学习人数")
    @ApiImplicitParam(name = "courseId", value = "课程id", required = true, dataTypeClass = Long.class)
    public Integer countLearningLessonByCourse(@PathVariable("courseId") Long courseId) {
        return lessonService.countLearningLessonByCourse(courseId);
    }
}
