package com.yun.aiinterview.pojo.vo;

public record InterviewQuestionVO(
        Integer questionIndex,
        String questionContent,
        String topicSummary,
        String type,
        String userAnswer
) {
}
