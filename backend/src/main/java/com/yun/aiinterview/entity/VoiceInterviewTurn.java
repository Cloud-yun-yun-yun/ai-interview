package com.yun.aiinterview.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Builder;
import lombok.Data;

/**
 * 语音面试回合记录（一问一答一条）。
 * 简历面试不设标准答案，故不含参考答案字段。
 */
@Data
@TableName("voice_interview_turn")
@Builder
public class VoiceInterviewTurn {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属语音面试会话 ID */
    @TableField("session_id")
    private String sessionId;

    /** 回合序号（从 1 开始） */
    @TableField("turn_index")
    private Integer turnIndex;

    /** 本题问题内容（AI 生成） */
    @TableField("question_content")
    private String questionContent;

    /** 本题问题音频存储 key（AI 题干 TTS 合成） */
    @TableField("question_audio_key")
    private String questionAudioKey;

    /** 用户回答音频存储 key（存于对象存储） */
    @TableField("user_audio_key")
    private String userAudioKey;

    /** 用户回答文本（ASR 识别结果） */
    @TableField("user_answer")
    private String userAnswer;

    /** 该题是否允许被追问 */
    @TableField("is_follow_up")
    private Boolean isFollowUp;
}