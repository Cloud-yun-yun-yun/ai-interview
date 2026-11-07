package com.yun.aiinterview.entity;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import com.yun.aiinterview.pojo.vo.AsyncTaskStatus;

import java.util.List;

@Data
@TableName(value = "interview_session", autoResultMap = true)
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class InterviewSession {
    // --- 状态机常量定义 ---
    // 已创建：基础题已落库，AI 出题中或已出题完毕
    public static final String STATUS_CREATED = "CREATED";
    // 已提交：用户已交卷，AI 判卷中或已判卷完毕
    public static final String STATUS_SUBMITTED = "SUBMITTED";
    // 已评估：AI 判卷完毕，分数和反馈已生成
    public static final String STATUS_EVALUATED = "EVALUATED";


    /** UUID，既是业务标识也是主键 */
    @TableId(value = "session_id", type = IdType.ASSIGN_UUID)
    private String sessionId;

    /** 隔离字段 */
    @TableField("user_id")
    private Long userId;

    /** 进度 */
    @TableField("current_index")
    private Integer currentIndex;

    /** 状态：CREATED / COMPLETED */
    @TableField("status")
    private String status;

    @TableField("total_questions")
    private Integer totalQuestions;

    @TableField("total_score")
    private Integer totalScore;

    @TableField("feedback")
    private String feedback;

    /** 简历 ID (可为空) */
    @TableField("resume_id")
    private Long resumeId;

    /** 关联的知识库 ID (可为空) */
    @TableField("kb_id")
    private Long kbId;

    /** 前端生成的 UUID，用于防重复提交 */
    @TableField("request_id")
    private String requestId;

    @TableField("generation_status")
    private AsyncTaskStatus generationStatus;

    /** 异步任务状态 */
    @TableField("async_task_status")
    private AsyncTaskStatus asyncTaskStatus;
}

