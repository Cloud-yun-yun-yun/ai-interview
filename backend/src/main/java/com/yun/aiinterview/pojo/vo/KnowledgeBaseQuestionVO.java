package com.yun.aiinterview.pojo.vo;

/**
 * 知识库题目响应（过滤掉 id 和 kbId）。
 */
public record KnowledgeBaseQuestionVO(
        String type,
        String question,
        String topicSummary,
        String referenceAnswer,
        String scoringRubric
) {
}