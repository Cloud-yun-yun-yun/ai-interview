package com.yun.aiinterview.entity;


import com.baomidou.mybatisplus.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import com.yun.aiinterview.pojo.vo.AsyncTaskStatus;

import java.time.LocalDateTime;

/**
 * 简历主表
 */
@Data
@TableName("resume")
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Resume {

    /** 单页 PDF 解析出的文本上限 */
    public static final int MAX_TEXT_LENGTH = 2500;
    public static final String REJECT_INTERVIEW = "REJECT";
    public static final String ACCEPT_INTERVIEW = "ACCEPT";

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 上传者 ID（逻辑外键 → user.id） */
    private Long userId;
    
    private AsyncTaskStatus asyncTaskStatus = AsyncTaskStatus.PENDING;

    /** 简历完整原文 */
    private String content;

    /** 解析出的文本字符数 */
    private Integer textLength;

    /** 文件 SHA-256 哈希（用于去重/校验） */
    private String fileHash;

    /** 简历名称 */
    private String name;

    /*0表示不可开启面试，1表示可以开启面试*/
    private String interviewStatus = REJECT_INTERVIEW;

}