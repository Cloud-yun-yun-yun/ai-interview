package com.yun.aiinterview.pojo.vo;

import java.time.LocalDateTime;

/**
 * 问题生成任务状态响应。
 */
public record QuestionGenStatusVO(
        Long knowledgeBaseId,
        QuestionGenStatus questionGenStatus,
        String questionGenTaskId
) {
}
