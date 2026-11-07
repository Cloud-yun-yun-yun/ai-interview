package com.yun.aiinterview.pojo.vo;

/**
 * 面试评估结果（用于前端轮询查询）。
 * status 非 COMPLETED 时，overallScore 与 feedback 为空，表示评估仍在进行或尚未提交。
 */
public record InterviewEvaluationVO(
        String sessionId,
        AsyncTaskStatus status,
        Integer overallScore,
        String feedback
) {
}