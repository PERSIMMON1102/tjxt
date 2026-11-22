package com.tianji.exam.controller;

import com.tianji.common.domain.dto.PageDTO;
import com.tianji.common.domain.query.PageQuery;
import com.tianji.exam.domain.dto.ExamCommitDTO;
import com.tianji.exam.domain.dto.ExamFormDTO;
import com.tianji.exam.domain.vo.ExamDetailVO;
import com.tianji.exam.domain.vo.ExamPageVO;
import com.tianji.exam.domain.vo.ExamVO;
import com.tianji.exam.service.IExamService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;
import java.util.List;

/**
 * 考试相关接口
 *
 * @author Tom
 */
@Api(tags = "考试相关接口")
@RestController
@RequestMapping("/exams")
@RequiredArgsConstructor
public class ExamController {

    private final IExamService examService;

    @ApiOperation("获取试题并开始考试")
    @PostMapping
    public ExamVO startExam(@Valid @RequestBody ExamFormDTO formDTO) {
        return examService.startExam(formDTO);
    }

    @ApiOperation("提交考试结果")
    @PostMapping("/details")
    public void commitExam(@Valid @RequestBody ExamCommitDTO commitDTO) {
        examService.commitExam(commitDTO);
    }

    @ApiOperation("分页查询考试记录")
    @GetMapping("/page")
    public PageDTO<ExamPageVO> queryExamPage(@Valid PageQuery query) {
        return examService.queryExamPage(query);
    }

    @ApiOperation("查询考试记录详情")
    @GetMapping("/{id}")
    public List<ExamDetailVO> queryExamDetail(
            @ApiParam(value = "考试记录id", example = "1578558664933920770") @PathVariable("id") String id) {
        return examService.queryExamDetail(id);
    }
}