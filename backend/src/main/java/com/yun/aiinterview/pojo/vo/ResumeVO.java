package com.yun.aiinterview.pojo.vo;

public record ResumeVO(
        String name,
        Long id,
        Long userId,
        Integer textLength,
        String interviewStatus,
        AsyncTaskStatus asyncTaskStatus
) {
}