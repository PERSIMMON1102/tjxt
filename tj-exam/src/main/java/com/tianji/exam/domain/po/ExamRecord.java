package com.tianji.exam.domain.po;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 考试记录，保存在 MongoDB 中
 *
 * @author Tom
 */
@Data
@Document("exam_record")
public class ExamRecord implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 考试记录id */
    @Id
    private String id;

    /** 学员id */
    private Long userId;

    /** 课程id */
    private Long courseId;

    /** 小节id */
    private Long sectionId;

    /** 课表id，交卷后通知学习服务时使用 */
    private Long lessonId;

    /** 考试类型：1-练习，2-考试 */
    private Integer type;

    /** 考试得分 */
    private Integer score;

    /** 考试用时，单位：分钟 */
    private Integer duration;

    /** 是否已经交卷 */
    private Boolean finished;

    /** 开始考试时间 */
    private LocalDateTime createTime;

    /** 交卷时间 */
    private LocalDateTime commitTime;

    /** 更新时间 */
    private LocalDateTime updateTime;

    /** 学员答题及批阅信息 */
    private List<ExamAnswer> answers;
}