package com.yun.aiinterview.pojo.vo;

public record KnowledgeBaseVO(
        Long id,
        String name,
        String fileSize,
        String uploadTime,
        AsyncTaskStatus vectorStatus,
        QuestionGenStatus questionGenStatus) {
}