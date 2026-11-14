package com.yun.aiinterview.pojo.dto;

import lombok.Builder;

@Builder
public record VoiceInterviewSubmitDTO(
        String sessionId,
        String userAudioKey,
        Integer turnIndex,
        String questionContent,
        String questionAudioKey,
        Long resumeId
) {
}