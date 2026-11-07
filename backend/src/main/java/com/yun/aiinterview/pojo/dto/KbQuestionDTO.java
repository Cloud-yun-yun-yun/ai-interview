package com.yun.aiinterview.pojo.dto;

import java.util.List;

public record KbQuestionDTO(List<Long> knowledgeBaseIds, String msg, String conversationId) {
}