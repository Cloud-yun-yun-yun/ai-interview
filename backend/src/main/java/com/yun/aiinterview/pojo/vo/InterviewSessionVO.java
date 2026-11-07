package com.yun.aiinterview.pojo.vo;

import java.util.List;

public record InterviewSessionVO(
        String sessionId,
        Long userId,
        Integer currentIndex,
        String status
) {
}
