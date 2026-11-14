package com.yun.aiinterview.pojo.vo;

import lombok.Builder;

@Builder
public record VoiceInterviewSessionVO(
        String sessionId,
        Long userId,
        Integer currentTurn,
        Integer totalTurns,
        String sessionStatus,
        AsyncTaskStatus evaluationStatus,
        Long resumeId,
        String introAudioKey,
        String createTime,
        String sessionName,
        Integer totalScore,
        String feedback
) {
}