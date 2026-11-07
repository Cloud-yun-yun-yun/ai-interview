package com.yun.aiinterview.entity;

import java.util.List;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;

@Data
@TableName(value = "resume_analysis_response", autoResultMap = true)
public class ResumeAnalysisResponse {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属简历 ID（逻辑外键 → resume.id） */
    private Long resumeId;

    private Long userId;

    private int overallScore;
    private int projectScore;
    private int skillMatchScore;
    private int contentScore;
    private int structureScore;
    private int expressionScore;
    private String summary;
    private Boolean allowReparse;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<Suggestion> suggestions;

    @Data
    public static class Suggestion {
        private String category;
        private String priority;
        private String issue;
        private String recommendation;
    }
}