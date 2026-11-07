package com.yun.aiinterview.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import com.yun.aiinterview.pojo.vo.QuestionGenStatus;
import com.yun.aiinterview.pojo.vo.AsyncTaskStatus;

import java.time.LocalDateTime;

/**
 * 知识库实体
 */
@Data
@TableName("knowledge_bases")
public class KnowledgeBase {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 文件内容的 SHA-256 哈希值，用于去重 */
    @TableField("file_hash")
    private String fileHash;

    /** 知识库名称（用户自定义或从文件名提取） */
    @TableField("name")
    private String name;

    /** 所属用户 ID */
    @TableField("user_id")
    private Long userId;

    /** 原始文件名 */
    @TableField("original_filename")
    private String originalFilename;

    /** 文件大小（字节） */
    @TableField("file_size")
    private Long fileSize;

    /** 文件类型 */
    @TableField("content_type")
    private String contentType;

    /** MinIo 存储的文件 Key */
    @TableField("storage_key")
    private String storageKey;

    /** 上传时间 */
    @TableField(value = "uploaded_at", fill = FieldFill.INSERT)
    private LocalDateTime uploadedAt;

    /** 向量化状态（新上传时为 PENDING，异步处理完成后变为 COMPLETED） */
    @TableField("vector_status")
    private AsyncTaskStatus vectorStatus = AsyncTaskStatus.PENDING;

    /** 向量分块数量 */
    @TableField("chunk_count")
    private Integer chunkCount = 0;

    /** 问题生成状态 */
    @TableField("question_gen_status")
    private QuestionGenStatus questionGenStatus = QuestionGenStatus.NONE;

    /** 问题生成任务 ID（用于幂等判断，防止旧任务覆盖新任务） */
    @TableField("question_gen_task_id")
    private String questionGenTaskId;

    /** 问题生成任务更新时间 */
    @TableField("question_gen_updated_at")
    private LocalDateTime questionGenUpdatedAt;

}