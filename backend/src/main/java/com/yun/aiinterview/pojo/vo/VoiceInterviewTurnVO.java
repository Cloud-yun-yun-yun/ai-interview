package com.yun.aiinterview.pojo.vo;

import lombok.Builder;

@Builder
public record VoiceInterviewTurnVO(
        String sessionId,
        Integer turnIndex,
        String questionContent,
        String questionAudioUrl
) {
}