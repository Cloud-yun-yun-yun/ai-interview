package com.yun.aiinterview.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@TableName(value = "knowledge_base_question", autoResultMap = true)
@Builder
public class KnowledgeBaseQuestion {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 知识库ID */
    private Long kbId;

    /** 题目类型（如 技术题、场景题、设计题） */
    private String type;

    /** 题干 */
    private String question;

    /** 该题覆盖的知识点摘要 */
    private String topicSummary;

    /** 参考答案 */
    private String referenceAnswer;

    /** 评分标准 */
    private String scoringRubric;
}
