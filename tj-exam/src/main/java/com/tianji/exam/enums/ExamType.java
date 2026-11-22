package com.tianji.exam.enums;

import com.tianji.common.enums.BaseEnum;
import lombok.Getter;

/**
 * 考试类型
 *
 * @author Tom
 */
@Getter
public enum ExamType implements BaseEnum {
    /** 视频小节中的练习，可以反复参加 */
    PRACTICE(1, "练习"),
    /** 章节末尾的测试小节，只能参加一次 */
    EXAM(2, "考试"),
    ;

    private final int value;
    private final String desc;

    ExamType(int value, String desc) {
        this.value = value;
        this.desc = desc;
    }

    public static ExamType of(Integer value) {
        if (value == null) {
            return null;
        }
        for (ExamType type : values()) {
            if (type.equalsValue(value)) {
                return type;
            }
        }
        return null;
    }
}