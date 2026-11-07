package com.yun.aiinterview.pojo.dto;

public record QuestionDTO(
        String type,
        String question,
        String topicSummary,
        String referenceAnswer,
        String scoringRubric
) {}