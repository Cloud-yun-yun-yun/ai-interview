package com.yun.aiinterview.pojo.vo;

public record InterviewSessionVO(
        String sessionId,
        Long userId,
        Integer currentIndex,
        String sessionStatus,
        AsyncTaskStatus sessionEvaluateStatus,
        AsyncTaskStatus sessionQuestionGenStatus,
        String createTime,
        String sessionName,
        String totalScore,
        String feedback
) {
}