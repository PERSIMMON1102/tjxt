package com.tianji.exam.service;

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
import com.tianji.common.utils.UserContext;
import com.tianji.exam.domain.dto.ExamAnswerFormDTO;
import com.tianji.exam.domain.dto.ExamCommitDTO;
import com.tianji.exam.domain.dto.ExamFormDTO;
import com.tianji.exam.domain.po.ExamAnswer;
import com.tianji.exam.domain.po.ExamRecord;
import com.tianji.exam.domain.vo.ExamDetailVO;
import com.tianji.exam.domain.vo.ExamPageVO;
import com.tianji.exam.domain.vo.ExamVO;
import com.tianji.exam.enums.ExamType;
import com.tianji.exam.service.impl.ExamServiceImpl;
import org.bson.Document;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 考试业务单元测试，重点验证开始考试的校验、交卷自动批阅及学习记录通知
 *
 * @author Tom
 */
@ExtendWith(MockitoExtension.class)
class ExamServiceImplTest {

    private static final Long USER_ID = 1L;
    private static final Long COURSE_ID = 10L;
    private static final Long SECTION_ID = 20L;
    private static final Long LESSON_ID = 30L;

    @Mock
    private MongoTemplate mongoTemplate;
    @Mock
    private IQuestionService questionService;
    @Mock
    private LearningClient learningClient;
    @Mock
    private CourseClient courseClient;
    @Mock
    private CatalogueClient catalogueClient;

    @InjectMocks
    private ExamServiceImpl examService;

    @BeforeEach
    void setUp() {
        UserContext.setUser(USER_ID);
    }

    @AfterEach
    void tearDown() {
        UserContext.removeUser();
    }

    @Test
    @DisplayName("未购买课程时不能开始考试")
    void startExam_shouldFailWhenCourseNotPurchased() {
        // given
        when(learningClient.isLessonValid(COURSE_ID)).thenReturn(null);
        ExamFormDTO form = examForm(ExamType.PRACTICE);
        // when & then
        assertThrows(BadRequestException.class, () -> examService.startExam(form));
        verify(mongoTemplate, never()).insert(any(ExamRecord.class));
    }

    @Test
    @DisplayName("章节测试只能参加一次")
    void startExam_shouldFailWhenExamAlreadyFinished() {
        // given
        when(learningClient.isLessonValid(COURSE_ID)).thenReturn(LESSON_ID);
        when(courseClient.getCourseInfoById(COURSE_ID, true, false))
                .thenReturn(courseWithSection(ExamConstantsHolder.CATA_PRACTICE));
        when(mongoTemplate.exists(any(Query.class), eq(ExamRecord.class))).thenReturn(true);
        ExamFormDTO form = examForm(ExamType.EXAM);
        // when & then
        assertThrows(BadRequestException.class, () -> examService.startExam(form));
        verify(mongoTemplate, never()).insert(any(ExamRecord.class));
    }

    @Test
    @DisplayName("开始考试成功，返回考试记录id及不含答案的题目")
    void startExam_shouldReturnQuestionsWithoutAnswer() {
        // given
        when(learningClient.isLessonValid(COURSE_ID)).thenReturn(LESSON_ID);
        when(courseClient.getCourseInfoById(COURSE_ID, true, false))
                .thenReturn(courseWithSection(ExamConstantsHolder.CATA_PRACTICE));
        when(mongoTemplate.exists(any(Query.class), eq(ExamRecord.class))).thenReturn(false);
        when(questionService.queryQuestionByBizId(SECTION_ID))
                .thenReturn(List.of(question(100L, "1", "A", 5, "选择题干")));
        ExamFormDTO form = examForm(ExamType.EXAM);
        // when
        ExamVO vo = examService.startExam(form);
        // then
        assertNotNull(vo);
        assertEquals(1, vo.getQuestions().size());
        assertEquals(1, vo.getQuestions().get(0).getType().intValue());
        assertEquals(5, vo.getQuestions().get(0).getScore().intValue());
        verify(mongoTemplate).insert(any(ExamRecord.class));
    }

    @Test
    @DisplayName("交卷时自动批阅客观题并通知学习服务")
    void commitExam_shouldGradeAndSaveLearningRecord() {
        // given
        ExamRecord record = examRecord(ExamType.EXAM);
        when(mongoTemplate.findById(record.getId(), ExamRecord.class)).thenReturn(record);
        when(questionService.queryQuestionByBizId(SECTION_ID)).thenReturn(List.of(
                question(100L, "1", "A", 5, "radio"),
                question(101L, "2", "A,B", 5, "multi"),
                question(102L, "5", "参考答案", 10, "subjective")
        ));
        when(mongoTemplate.findAndModify(any(Query.class), any(Update.class),
                any(FindAndModifyOptions.class), eq(ExamRecord.class))).thenReturn(record);
        ExamCommitDTO commitDTO = new ExamCommitDTO();
        commitDTO.setId(record.getId());
        commitDTO.setExamDetails(List.of(
                answer(100L, "1", "a"),
                answer(101L, "2", "B, A")
        ));
        // when
        examService.commitExam(commitDTO);
        // then
        ArgumentCaptor<Update> captor = ArgumentCaptor.forClass(Update.class);
        verify(mongoTemplate).findAndModify(any(Query.class), captor.capture(),
                any(FindAndModifyOptions.class), eq(ExamRecord.class));
        Document set = (Document) captor.getValue().getUpdateObject().get("$set");
        assertEquals(10, ((Number) set.get("score")).intValue());
        assertEquals(true, set.get("finished"));
        assertTrue(((Number) set.get("duration")).intValue() >= 10);
        verify(learningClient).saveLearningRecord(any(LearningRecordFormDTO.class));
    }

    @Test
    @DisplayName("练习交卷不通知学习服务")
    void commitExam_shouldNotSaveLearningRecordForPractice() {
        // given
        ExamRecord record = examRecord(ExamType.PRACTICE);
        when(mongoTemplate.findById(record.getId(), ExamRecord.class)).thenReturn(record);
        when(questionService.queryQuestionByBizId(SECTION_ID))
                .thenReturn(List.of(question(100L, "4", "1", 5, "judge")));
        when(mongoTemplate.findAndModify(any(Query.class), any(Update.class),
                any(FindAndModifyOptions.class), eq(ExamRecord.class))).thenReturn(record);
        ExamCommitDTO commitDTO = new ExamCommitDTO();
        commitDTO.setId(record.getId());
        commitDTO.setExamDetails(List.of(answer(100L, "4", "1")));
        // when
        examService.commitExam(commitDTO);
        // then
        verify(learningClient, never()).saveLearningRecord(any(LearningRecordFormDTO.class));
    }

    @Test
    @DisplayName("重复交卷时抛出异常，且不会再次更新记录")
    void commitExam_shouldFailWhenAlreadyFinished() {
        // given
        ExamRecord record = examRecord(ExamType.EXAM);
        record.setFinished(true);
        when(mongoTemplate.findById(record.getId(), ExamRecord.class)).thenReturn(record);
        ExamCommitDTO commitDTO = new ExamCommitDTO();
        commitDTO.setId(record.getId());
        commitDTO.setExamDetails(List.of(answer(100L, "1", "A")));
        // when & then
        assertThrows(BadRequestException.class, () -> examService.commitExam(commitDTO));
        verify(mongoTemplate, never()).findAndModify(any(Query.class), any(Update.class),
                any(FindAndModifyOptions.class), eq(ExamRecord.class));
    }

    @Test
    @DisplayName("考试记录不存在时抛出异常")
    void queryExamDetail_shouldFailWhenRecordNotExists() {
        when(mongoTemplate.findById(anyString(), eq(ExamRecord.class))).thenReturn(null);
        assertThrows(BadRequestException.class,
                () -> examService.queryExamDetail("507f1f77bcf86cd799439011"));
    }

    @Test
    @DisplayName("未交卷时不能查看考试详情")
    void queryExamDetail_shouldFailWhenNotFinished() {
        ExamRecord record = examRecord(ExamType.EXAM);
        record.setFinished(false);
        when(mongoTemplate.findById(record.getId(), ExamRecord.class)).thenReturn(record);
        assertThrows(BadRequestException.class, () -> examService.queryExamDetail(record.getId()));
    }

    @Test
    @DisplayName("分页查询考试记录，填充课程和小节名称")
    void queryExamPage_shouldFillNames() {
        // given
        PageQuery query = new PageQuery();
        query.setPageNo(1);
        query.setPageSize(5);
        ExamRecord record = examRecord(ExamType.EXAM);
        record.setFinished(true);
        record.setScore(10);
        record.setDuration(10);
        record.setCommitTime(LocalDateTime.now());
        when(mongoTemplate.count(any(Query.class), eq(ExamRecord.class))).thenReturn(1L);
        when(mongoTemplate.find(any(Query.class), eq(ExamRecord.class))).thenReturn(List.of(record));
        CourseSimpleInfoDTO course = new CourseSimpleInfoDTO();
        course.setId(COURSE_ID);
        course.setName("Redis从入门到实战");
        when(courseClient.getSimpleInfoList(any())).thenReturn(List.of(course));
        CataSimpleInfoDTO section = new CataSimpleInfoDTO();
        section.setId(SECTION_ID);
        section.setName("Redis的常用数据类型");
        when(catalogueClient.batchQueryCatalogue(any())).thenReturn(List.of(section));
        // when
        PageDTO<ExamPageVO> page = examService.queryExamPage(query);
        // then
        assertEquals(1L, page.getTotal().longValue());
        assertEquals(1L, page.getPages().longValue());
        assertEquals(1, page.getList().size());
        assertEquals("Redis从入门到实战", page.getList().get(0).getCourseName());
        assertEquals("Redis的常用数据类型", page.getList().get(0).getSectionName());
    }

    @Test
    @DisplayName("查询考试详情，返回作答及题目信息")
    void queryExamDetail_shouldReturnAnswersAndQuestions() {
        // given
        ExamRecord record = examRecord(ExamType.EXAM);
        record.setFinished(true);
        ExamAnswer answer = new ExamAnswer();
        answer.setQuestionId(100L);
        answer.setQuestionType(1);
        answer.setAnswer("A");
        answer.setCorrect(true);
        answer.setScore(5);
        answer.setComment("无");
        record.setAnswers(List.of(answer));
        when(mongoTemplate.findById(record.getId(), ExamRecord.class)).thenReturn(record);
        QuestionDTO question = question(100L, "1", "A", 5, "Redis的常用数据类型有哪些");
        question.setOptions(List.of("A", "B"));
        question.setAnalysis("考察Redis数据类型");
        when(questionService.queryQuestionByIds(List.of(100L))).thenReturn(List.of(question));
        // when
        List<ExamDetailVO> details = examService.queryExamDetail(record.getId());
        // then
        assertEquals(1, details.size());
        assertEquals("A", details.get(0).getAnswer());
        assertTrue(details.get(0).getCorrect());
        assertEquals(5, details.get(0).getScore().intValue());
        assertEquals("Redis的常用数据类型有哪些", details.get(0).getQuestion().getName());
        assertEquals("考察Redis数据类型", details.get(0).getQuestion().getAnalysis());
        assertEquals(1, details.get(0).getQuestion().getType().intValue());
    }

    private ExamFormDTO examForm(ExamType type) {
        ExamFormDTO form = new ExamFormDTO();
        form.setCourseId(COURSE_ID);
        form.setSectionId(SECTION_ID);
        form.setType(type.getValue());
        return form;
    }

    private ExamRecord examRecord(ExamType type) {
        ExamRecord record = new ExamRecord();
        record.setId("507f1f77bcf86cd799439011");
        record.setUserId(USER_ID);
        record.setCourseId(COURSE_ID);
        record.setSectionId(SECTION_ID);
        record.setLessonId(LESSON_ID);
        record.setType(type.getValue());
        record.setFinished(false);
        record.setScore(0);
        record.setCreateTime(LocalDateTime.now().minusMinutes(10));
        return record;
    }

    private CourseFullInfoDTO courseWithSection(Integer cataType) {
        CatalogueDTO section = new CatalogueDTO();
        section.setId(SECTION_ID);
        section.setType(cataType);
        CatalogueDTO chapter = new CatalogueDTO();
        chapter.setId(1L);
        chapter.setSections(List.of(section));
        CourseFullInfoDTO course = new CourseFullInfoDTO();
        course.setId(COURSE_ID);
        course.setChapters(List.of(chapter));
        return course;
    }

    private QuestionDTO question(Long id, String type, String answer, int score, String name) {
        QuestionDTO question = new QuestionDTO();
        question.setId(id);
        question.setName(name);
        question.setType(type);
        question.setAnswer(answer);
        question.setScore(score);
        question.setDifficulty(1);
        return question;
    }

    private ExamAnswerFormDTO answer(Long questionId, String type, String answer) {
        ExamAnswerFormDTO dto = new ExamAnswerFormDTO();
        dto.setQuestionId(questionId);
        dto.setQuestionType(Integer.valueOf(type));
        dto.setAnswer(answer);
        return dto;
    }

    /**
     * 课程目录类型常量，避免测试中直接依赖课程服务
     */
    private interface ExamConstantsHolder {
        int CATA_PRACTICE = 3;
    }
}