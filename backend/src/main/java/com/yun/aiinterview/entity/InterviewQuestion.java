package com.yun.aiinterview.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Builder;
import lombok.Data;

@Data
@TableName("interview_question")
@Builder
public class InterviewQuestion {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String sessionId;

    private Integer questionIndex;

    private String questionContent;

    private String topicSummary;

    private String type;

    private String userAnswer;

    private String scoringRubric;

    private String referenceAnswer;
}

