package com.yun.aiinterview.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.yun.aiinterview.pojo.vo.AsyncTaskStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 语音面试会话（回合制）。
 * 针对简历进行一对一语音模拟面试，一问一答为一回合。
 */
@Data
@TableName(value = "voice_interview_session", autoResultMap = true)
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class VoiceInterviewSession {

    // --- 状态机常量定义 ---
    /** 已创建：第 1 题已准备，等待开始 */
    public static final String STATUS_READY = "READY";
    /** 进行中：已进入回合问答 */
    public static final String STATUS_IN_PROGRESS = "IN_PROGRESS";
    /** 已结束：所有回合作答完毕，等待/已完成评估 */
    public static final String STATUS_FINISHED = "FINISHED";

    /** UUID，既是业务标识也是主键 */
    @TableId(value = "session_id", type = IdType.ASSIGN_UUID)
    private String sessionId;

    /** 隔离字段 */
    @TableField("user_id")
    private Long userId;

    /** 会话状态：READY / IN_PROGRESS / FINISHED */
    @TableField("session_status")
    private String sessionStatus;

    /** 总回合数 */
    @TableField("total_turns")
    private Integer totalTurns;

    /** 当前回合（从 1 开始） */
    @TableField("current_turn")
    private Integer currentTurn;

    /** 依据的简历 ID（简历面无标准答案） */
    @TableField("resume_id")
    private Long resumeId;

    /** 首题（固定开场自我介绍）音频存储 key，会话创建时生成固定落库 */
    @TableField("intro_audio_key")
    private String introAudioKey;

    /** 异步评估状态 */
    @TableField("evaluation_status")
    private AsyncTaskStatus evaluationStatus;

    /** 总分（评估完成后写入） */
    @TableField("total_score")
    private Integer totalScore;

    /** 整体反馈（评估完成后写入） */
    @TableField("feedback")
    private String feedback;

    /** 会话名称（简历名 + 语音面试） */
    @TableField("session_name")
    private String sessionName;

    /** 会话创建时间 */
    @TableField("create_time")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;
}