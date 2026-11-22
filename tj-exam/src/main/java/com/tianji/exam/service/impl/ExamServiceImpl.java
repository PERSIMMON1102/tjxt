package com.tianji.exam.service.impl;

import com.tianji.api.client.course.CatalogueClient;
import com.tianji.api.client.course.CourseClient;
import com.tianji.api.client.learning.LearningClient;
import com.tianji.api.dto.course.CataSimpleInfoDTO;
import com.tianji.api.dto.course.CatalogueDTO;
import com.tianji.api.dto.course.CourseFullInfoDTO;
import com.tianji.api.dto.course.CourseSimpleInfoDTO;
import com.tianji.api.dto.exam.QuestionDTO;
import com.tianji.api.dto.leanring.LearningRecordFormDTO;
import com.tianji.common.domain.dto.PageDTO;
import com.tianji.common.domain.query.PageQuery;
import com.tianji.common.exceptions.BadRequestException;
import com.tianji.common.utils.AssertUtils;
import com.tianji.common.utils.BeanUtils;
import com.tianji.common.utils.BooleanUtils;
import com.tianji.common.utils.CollUtils;
import com.tianji.common.utils.StringUtils;
import com.tianji.common.utils.UserContext;
import com.tianji.exam.constants.ExamConstants;
import com.tianji.exam.constants.QuestionType;
import com.tianji.exam.domain.dto.ExamAnswerFormDTO;
import com.tianji.exam.domain.dto.ExamCommitDTO;
import com.tianji.exam.domain.dto.ExamFormDTO;
import com.tianji.exam.domain.po.ExamAnswer;
import com.tianji.exam.domain.po.ExamRecord;
import com.tianji.exam.domain.vo.ExamDetailVO;
import com.tianji.exam.domain.vo.ExamPageVO;
import com.tianji.exam.domain.vo.ExamQuestionDetailVO;
import com.tianji.exam.domain.vo.ExamQuestionVO;
import com.tianji.exam.domain.vo.ExamVO;
import com.tianji.exam.enums.ExamType;
import com.tianji.exam.service.IExamService;
import com.tianji.exam.service.IQuestionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.types.ObjectId;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import static com.tianji.exam.constants.ExamErrorInfo.EXAM_ALREADY_FINISHED;
import static com.tianji.exam.constants.ExamErrorInfo.EXAM_COURSE_NOT_EXISTS;
import static com.tianji.exam.constants.ExamErrorInfo.EXAM_COURSE_NOT_PURCHASED;
import static com.tianji.exam.constants.ExamErrorInfo.EXAM_NOT_FINISHED;
import static com.tianji.exam.constants.ExamErrorInfo.EXAM_NO_PERMISSION;
import static com.tianji.exam.constants.ExamErrorInfo.EXAM_NO_QUESTION;
import static com.tianji.exam.constants.ExamErrorInfo.EXAM_ONLY_ONCE;
import static com.tianji.exam.constants.ExamErrorInfo.EXAM_RECORD_NOT_EXISTS;
import static com.tianji.exam.constants.ExamErrorInfo.EXAM_SECTION_NOT_EXISTS;
import static com.tianji.exam.constants.ExamErrorInfo.EXAM_TYPE_ILLEGAL;

/**
 * 考试相关业务实现
 *
 * @author Tom
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ExamServiceImpl implements IExamService {

    private final MongoTemplate mongoTemplate;
    private final IQuestionService questionService;
    private final LearningClient learningClient;
    private final CourseClient courseClient;
    private final CatalogueClient catalogueClient;

    @Override
    public ExamVO startExam(ExamFormDTO formDTO) {
        // 1.获取当前用户
        Long userId = UserContext.getUser();
        AssertUtils.isNotNull(userId, "请先登录");
        // 2.校验考试类型
        ExamType examType = ExamType.of(formDTO.getType());
        if (examType == null) {
            throw new BadRequestException(EXAM_TYPE_ILLEGAL);
        }
        // 3.校验用户是否购买了课程，并获取课表id，交卷后新增学习记录时使用
        Long lessonId = learningClient.isLessonValid(formDTO.getCourseId());
        if (lessonId == null) {
            throw new BadRequestException(EXAM_COURSE_NOT_PURCHASED);
        }
        // 4.校验小节是否属于该课程
        CourseFullInfoDTO course = courseClient.getCourseInfoById(formDTO.getCourseId(), true, false);
        if (course == null) {
            throw new BadRequestException(EXAM_COURSE_NOT_EXISTS);
        }
        CatalogueDTO section = findSection(course, formDTO.getSectionId());
        if (section == null) {
            throw new BadRequestException(EXAM_SECTION_NOT_EXISTS);
        }
        // 5.章节末尾的测试只能参加一次，普通小节的练习可以反复参加
        if (examType == ExamType.EXAM && isExamFinished(userId, formDTO.getSectionId())) {
            throw new BadRequestException(EXAM_ONLY_ONCE);
        }
        // 6.查询小节下的题目
        List<QuestionDTO> questions = questionService.queryQuestionByBizId(formDTO.getSectionId());
        if (CollUtils.isEmpty(questions)) {
            throw new BadRequestException(EXAM_NO_QUESTION);
        }
        // 7.写入考试记录，记录考试开始时间
        ExamRecord record = new ExamRecord();
        record.setUserId(userId);
        record.setCourseId(formDTO.getCourseId());
        record.setSectionId(formDTO.getSectionId());
        record.setLessonId(lessonId);
        record.setType(examType.getValue());
        record.setScore(0);
        record.setFinished(false);
        LocalDateTime now = LocalDateTime.now();
        record.setCreateTime(now);
        record.setUpdateTime(now);
        mongoTemplate.insert(record);
        // 8.返回考试记录id及题目，题目中不包含答案
        ExamVO vo = new ExamVO();
        vo.setId(record.getId());
        vo.setQuestions(BeanUtils.copyList(questions, ExamQuestionVO.class));
        return vo;
    }

    @Override
    public void commitExam(ExamCommitDTO commitDTO) {
        // 1.获取用户并校验考试记录
        Long userId = UserContext.getUser();
        AssertUtils.isNotNull(userId, "请先登录");
        ExamRecord record = queryExamRecord(commitDTO.getId());
        if (!Objects.equals(record.getUserId(), userId)) {
            throw new BadRequestException(EXAM_NO_PERMISSION);
        }
        // 2.幂等校验，防止重复提交
        if (BooleanUtils.isTrue(record.getFinished())) {
            throw new BadRequestException(EXAM_ALREADY_FINISHED);
        }
        // 3.查询考试题目
        List<QuestionDTO> questions = questionService.queryQuestionByBizId(record.getSectionId());
        if (CollUtils.isEmpty(questions)) {
            throw new BadRequestException(EXAM_NO_QUESTION);
        }
        // 4.自动批阅
        Map<Long, ExamAnswerFormDTO> detailMap = new HashMap<>(commitDTO.getExamDetails().size());
        for (ExamAnswerFormDTO detail : commitDTO.getExamDetails()) {
            detailMap.put(detail.getQuestionId(), detail);
        }
        List<ExamAnswer> answers = new ArrayList<>(questions.size());
        int totalScore = 0;
        for (QuestionDTO question : questions) {
            ExamAnswerFormDTO detail = detailMap.get(question.getId());
            String userAnswer = detail == null || detail.getAnswer() == null ? "" : detail.getAnswer().trim();
            Integer questionType = parseQuestionType(question.getType());
            ExamAnswer answer = new ExamAnswer();
            answer.setQuestionId(question.getId());
            answer.setQuestionType(questionType);
            answer.setAnswer(userAnswer);
            if (questionType != null && questionType == QuestionType.SUBJECTIVE.getValue()) {
                // 主观题需要老师批阅，暂不计分
                answer.setCorrect(null);
                answer.setScore(0);
                answer.setComment("待批阅");
            } else {
                boolean correct = isCorrect(userAnswer, question.getAnswer(), questionType);
                int score = correct && question.getScore() != null ? question.getScore() : 0;
                answer.setCorrect(correct);
                answer.setScore(score);
                answer.setComment("无");
                totalScore += score;
            }
            answers.add(answer);
        }
        // 5.更新考试记录，更新条件中带上finished=false，保证并发提交时只有一次生效
        LocalDateTime commitTime = LocalDateTime.now();
        int duration = calcDuration(record.getCreateTime(), commitTime);
        Query query = Query.query(Criteria.where("_id").is(record.getId()).and("finished").is(false));
        Update update = new Update()
                .set("finished", true)
                .set("score", totalScore)
                .set("duration", duration)
                .set("commitTime", commitTime)
                .set("updateTime", commitTime)
                .set("answers", answers);
        ExamRecord updated = mongoTemplate.findAndModify(
                query, update, FindAndModifyOptions.options().returnNew(true), ExamRecord.class);
        if (updated == null) {
            throw new BadRequestException(EXAM_ALREADY_FINISHED);
        }
        // 6.考试小节属于课程学习的一部分，交卷后通知学习服务新增学习记录
        if (ExamType.of(record.getType()) == ExamType.EXAM) {
            saveLearningRecord(record, commitTime);
        }
    }

    @Override
    public PageDTO<ExamPageVO> queryExamPage(PageQuery query) {
        // 1.获取当前用户
        Long userId = UserContext.getUser();
        AssertUtils.isNotNull(userId, "请先登录");
        // 2.分页查询已交卷的考试记录，按交卷时间倒序
        Criteria criteria = Criteria.where("userId").is(userId).and("finished").is(true);
        long total = mongoTemplate.count(Query.query(criteria), ExamRecord.class);
        long pages = total == 0 ? 0 : (total + query.getPageSize() - 1) / query.getPageSize();
        if (total == 0) {
            return PageDTO.empty(total, pages);
        }
        Query pageQuery = Query.query(criteria)
                .with(Sort.by(Sort.Direction.DESC, "commitTime"))
                .skip(query.from())
                .limit(query.getPageSize());
        List<ExamRecord> records = mongoTemplate.find(pageQuery, ExamRecord.class);
        if (CollUtils.isEmpty(records)) {
            return PageDTO.empty(total, pages);
        }
        // 3.查询课程、小节名称
        Set<Long> courseIds = new HashSet<>();
        Set<Long> sectionIds = new HashSet<>();
        for (ExamRecord record : records) {
            courseIds.add(record.getCourseId());
            sectionIds.add(record.getSectionId());
        }
        Map<Long, String> courseNameMap = queryCourseNames(courseIds);
        Map<Long, String> sectionNameMap = querySectionNames(sectionIds);
        // 4.转换VO
        List<ExamPageVO> list = new ArrayList<>(records.size());
        for (ExamRecord record : records) {
            ExamPageVO vo = BeanUtils.toBean(record, ExamPageVO.class);
            vo.setCourseName(courseNameMap.get(record.getCourseId()));
            vo.setSectionName(sectionNameMap.get(record.getSectionId()));
            list.add(vo);
        }
        return new PageDTO<>(total, pages, list);
    }

    @Override
    public List<ExamDetailVO> queryExamDetail(String id) {
        // 1.获取用户并校验考试记录
        Long userId = UserContext.getUser();
        AssertUtils.isNotNull(userId, "请先登录");
        ExamRecord record = queryExamRecord(id);
        if (!Objects.equals(record.getUserId(), userId)) {
            throw new BadRequestException(EXAM_NO_PERMISSION);
        }
        // 2.未交卷不允许查看答案
        if (!BooleanUtils.isTrue(record.getFinished())) {
            throw new BadRequestException(EXAM_NOT_FINISHED);
        }
        List<ExamAnswer> answers = record.getAnswers();
        if (CollUtils.isEmpty(answers)) {
            return CollUtils.emptyList();
        }
        // 3.查询题目信息
        List<Long> questionIds = answers.stream()
                .map(ExamAnswer::getQuestionId)
                .collect(Collectors.toList());
        List<QuestionDTO> questions = questionService.queryQuestionByIds(questionIds);
        Map<Long, QuestionDTO> questionMap = new HashMap<>(questions.size());
        for (QuestionDTO question : questions) {
            questionMap.put(question.getId(), question);
        }
        // 4.封装结果
        List<ExamDetailVO> list = new ArrayList<>(answers.size());
        for (ExamAnswer answer : answers) {
            ExamDetailVO vo = new ExamDetailVO();
            vo.setAnswer(answer.getAnswer());
            vo.setComment(answer.getComment());
            vo.setCorrect(answer.getCorrect());
            vo.setScore(answer.getScore());
            QuestionDTO question = questionMap.get(answer.getQuestionId());
            if (question != null) {
                ExamQuestionDetailVO questionVO = new ExamQuestionDetailVO();
                questionVO.setId(question.getId());
                questionVO.setName(question.getName());
                questionVO.setType(parseQuestionType(question.getType()));
                questionVO.setScore(question.getScore());
                questionVO.setOptions(question.getOptions());
                questionVO.setAnswer(question.getAnswer());
                questionVO.setAnalysis(question.getAnalysis());
                questionVO.setDifficulty(question.getDifficulty());
                vo.setQuestion(questionVO);
            }
            list.add(vo);
        }
        return list;
    }

    /**
     * 查找课程中的指定小节
     */
    private CatalogueDTO findSection(CourseFullInfoDTO course, Long sectionId) {
        if (CollUtils.isEmpty(course.getChapters())) {
            return null;
        }
        for (CatalogueDTO chapter : course.getChapters()) {
            if (chapter == null || CollUtils.isEmpty(chapter.getSections())) {
                continue;
            }
            for (CatalogueDTO section : chapter.getSections()) {
                if (section != null && Objects.equals(section.getId(), sectionId)) {
                    return section;
                }
            }
        }
        return null;
    }

    /**
     * 判断用户是否已经完成过该小节的考试
     */
    private boolean isExamFinished(Long userId, Long sectionId) {
        Query query = Query.query(Criteria.where("userId").is(userId)
                .and("sectionId").is(sectionId)
                .and("type").is(ExamType.EXAM.getValue())
                .and("finished").is(true));
        return mongoTemplate.exists(query, ExamRecord.class);
    }

    /**
     * 查询考试记录，不存在或id格式错误时抛出异常
     */
    private ExamRecord queryExamRecord(String id) {
        if (StringUtils.isBlank(id) || !ObjectId.isValid(id)) {
            throw new BadRequestException(EXAM_RECORD_NOT_EXISTS);
        }
        ExamRecord record = mongoTemplate.findById(id, ExamRecord.class);
        if (record == null) {
            throw new BadRequestException(EXAM_RECORD_NOT_EXISTS);
        }
        return record;
    }

    /**
     * 判断客观题答案是否正确
     */
    private boolean isCorrect(String userAnswer, String rightAnswer, Integer questionType) {
        if (StringUtils.isBlank(userAnswer) || StringUtils.isBlank(rightAnswer)) {
            return false;
        }
        userAnswer = userAnswer.trim();
        rightAnswer = rightAnswer.trim();
        // 单选题、判断题答案唯一，直接比较
        if (questionType == null
                || questionType == QuestionType.RADIO.getValue()
                || questionType == QuestionType.JUDGE.getValue()) {
            return userAnswer.equalsIgnoreCase(rightAnswer);
        }
        // 多选题、不定项选择题的答案与顺序无关
        return splitAnswer(userAnswer).equals(splitAnswer(rightAnswer));
    }

    private Set<String> splitAnswer(String answer) {
        return Arrays.stream(answer.split("[,，]"))
                .map(String::trim)
                .filter(StringUtils::isNotBlank)
                .collect(Collectors.toSet());
    }

    /**
     * 题目类型在 QuestionDTO 中是字符串，这里转换为数字
     */
    private Integer parseQuestionType(String type) {
        if (StringUtils.isBlank(type)) {
            return null;
        }
        try {
            return Integer.valueOf(type.trim());
        } catch (NumberFormatException e) {
            log.warn("题目类型格式错误：{}", type);
            return null;
        }
    }

    /**
     * 计算考试用时，单位分钟
     */
    private int calcDuration(LocalDateTime createTime, LocalDateTime commitTime) {
        if (createTime == null) {
            return 0;
        }
        long minutes = ChronoUnit.MINUTES.between(createTime, commitTime);
        return (int) Math.max(minutes, 0);
    }

    private Map<Long, String> queryCourseNames(Collection<Long> courseIds) {
        Map<Long, String> map = new HashMap<>();
        if (CollUtils.isEmpty(courseIds)) {
            return map;
        }
        List<CourseSimpleInfoDTO> courses = courseClient.getSimpleInfoList(courseIds);
        if (CollUtils.isEmpty(courses)) {
            return map;
        }
        for (CourseSimpleInfoDTO course : courses) {
            if (course != null && course.getId() != null) {
                map.put(course.getId(), course.getName());
            }
        }
        return map;
    }

    private Map<Long, String> querySectionNames(Collection<Long> sectionIds) {
        Map<Long, String> map = new HashMap<>();
        if (CollUtils.isEmpty(sectionIds)) {
            return map;
        }
        List<CataSimpleInfoDTO> sections = catalogueClient.batchQueryCatalogue(sectionIds);
        if (CollUtils.isEmpty(sections)) {
            return map;
        }
        for (CataSimpleInfoDTO section : sections) {
            if (section != null && section.getId() != null) {
                map.put(section.getId(), section.getName());
            }
        }
        return map;
    }

    /**
     * 通知学习服务新增学习记录。
     * 学习记录不属于考试核心流程，失败时只记录日志，不影响考试结果。
     */
    private void saveLearningRecord(ExamRecord record, LocalDateTime commitTime) {
        try {
            Long lessonId = record.getLessonId();
            if (lessonId == null) {
                lessonId = learningClient.isLessonValid(record.getCourseId());
            }
            if (lessonId == null) {
                log.warn("考试记录 {} 对应的课表不存在，无法新增学习记录", record.getId());
                return;
            }
            LearningRecordFormDTO recordDTO = new LearningRecordFormDTO();
            recordDTO.setSectionType(ExamConstants.SectionType.EXAM);
            recordDTO.setLessonId(lessonId);
            recordDTO.setSectionId(record.getSectionId());
            recordDTO.setCommitTime(commitTime);
            learningClient.saveLearningRecord(recordDTO);
        } catch (Exception e) {
            log.error("保存考试学习记录失败，考试记录id：{}", record.getId(), e);
        }
    }
}