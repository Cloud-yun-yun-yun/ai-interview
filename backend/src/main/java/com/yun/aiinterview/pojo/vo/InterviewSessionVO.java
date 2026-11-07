package com.yun.aiinterview.pojo.vo;

public record InterviewSessionVO(
        String sessionId,
        Long userId,
        Integer currentIndex,
        String status,
        AsyncTaskStatus generationStatus
) {
}
