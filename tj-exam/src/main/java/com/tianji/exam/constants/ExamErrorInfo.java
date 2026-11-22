package com.tianji.exam.constants;

public interface ExamErrorInfo {
    String QUESTION_NOT_EXISTS = "题目不存在";

    String EXAM_RECORD_NOT_EXISTS = "考试记录不存在";
    String EXAM_NOT_FINISHED = "考试尚未提交，无法查看详情";
    String EXAM_ALREADY_FINISHED = "考试已提交，请勿重复提交";
    String EXAM_NO_PERMISSION = "无权操作该考试记录";

    String EXAM_TYPE_ILLEGAL = "考试类型错误，只能是：1-练习，2-考试";
    String EXAM_COURSE_NOT_PURCHASED = "您尚未购买该课程，无法参加考试";
    String EXAM_COURSE_NOT_EXISTS = "课程不存在";
    String EXAM_SECTION_NOT_EXISTS = "小节不存在";
    String EXAM_ONLY_ONCE = "该考试只能参加一次";
    String EXAM_NO_QUESTION = "该小节下没有题目";
}