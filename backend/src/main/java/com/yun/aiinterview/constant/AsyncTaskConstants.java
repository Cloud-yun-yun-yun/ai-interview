package com.yun.aiinterview.constant;

public class AsyncTaskConstants {

    public static final String FIELD_RETRY_COUNT = "retryCount";

    public static final String FIELD_TASK_ID = "taskId";

    public static final String FIELD_KB_ID = "kbId";

    public static final String FIELD_CONTENT = "content";

    public static final String FIELD_RESUME_ID = "resumeId";

    public static final String FIELD_USER_ID = "userId";

    public static final String FIELD_SESSION_ID = "sessionId";

    public static final String FIELD_TURN_INDEX = "turnIndex";
    //Stream消费消息的参数

    /**
     * 最大重试次数
     */
    public static final int MAX_RETRY_COUNT = 3;
    /**
     * 每次拉取的消息批次大小
     */
    public static final int BATCH_SIZE = 10;
    /**
     * 消费者轮询间隔（毫秒）
     */
    public static final long POLL_INTERVAL_MS = 1000;
    /**
     * Pending 消息空闲超过该时间后允许被当前消费者认领（毫秒）
     */
    public static final long PENDING_IDLE_TIMEOUT_MS = 5 * 60 * 1000;

    /**
     * 每轮最多回收的 pending 消息数
     */
    public static final int PENDING_CLAIM_BATCH_SIZE = 10;
    /**
     * 知识库问题生成 Stream Key
     */
    public static final String KB_QUESTION_GEN_STREAM_KEY = "knowledgebase:question-gen:stream";
    /**
     * Stream 最大长度（自动裁剪旧消息，防止无限增长）
     */
    public static final int STREAM_MAX_LEN = 1000;

    public static final String KB_QUESTION_GEN_CONSUMER_PREFIX = "question-gen-consumer-";
    public static final String KB_QUESTION_GEN_GROUP_NAME = "question-gen-group";
    /**
     * 知识库向量化 Stream Key
     */
    public static final String KB_VECTORIZE_STREAM_KEY = "knowledgebase:vectorize:stream";
    /**
     * 知识库向量化 Consumer Group 名称
     */
    public static final String KB_VECTORIZE_GROUP_NAME = "vectorize-group";

    /**
     * 知识库向量化 Consumer 名称前缀
     */
    public static final String KB_VECTORIZE_CONSUMER_PREFIX = "vectorize-consumer-";

    /**
     * 简历分析 Stream Key
     */
    public static final String RESUME_ANALYSIS_STREAM_KEY = "resume:analysis:stream";

    /**
     * 简历分析 Consumer Group 名称
     */
    public static final String RESUME_ANALYSIS_GROUP_NAME = "resume-analysis-group";

    /**
     * 简历分析 Consumer 名称前缀
     */
    public static final String RESUME_ANALYSIS_CONSUMER_PREFIX = "resume-analysis-consumer-";

    /**
     * 面试评估 Stream Key
     */
    public static final String INTERVIEW_EVALUATION_STREAM_KEY = "interview:evaluation:stream";
    /**
     * 面试评估 Consumer Group 名称
     */
    public static final String INTERVIEW_EVALUATION_GROUP_NAME = "interview-evaluation-group";
    /**
     * 面试评估 Consumer 名称前缀
     */
    public static final String INTERVIEW_EVALUATION_CONSUMER_PREFIX = "interview-evaluation-consumer-";

    /**
     * 面试出题 Stream Key
     */
    public static final String INTERVIEW_QUESTION_GEN_STREAM_KEY = "interview:question-gen:stream";
    /**
     * 面试出题 Consumer Group 名称
     */
    public static final String INTERVIEW_QUESTION_GEN_GROUP_NAME = "interview-question-gen-group";
    /**
     * 面试出题 Consumer 名称前缀
     */
    public static final String INTERVIEW_QUESTION_GEN_CONSUMER_PREFIX = "interview-question-gen-consumer-";

    /**
     * 语音面试出题 Stream Key
     */
    public static final String VOICE_QUESTION_GEN_STREAM_KEY = "voice:question-gen:stream";
    /**
     * 语音面试出题 Consumer Group 名称
     */
    public static final String VOICE_QUESTION_GEN_GROUP_NAME = "voice-question-gen-group";
    /**
     * 语音面试出题 Consumer 名称前缀
     */
    public static final String VOICE_QUESTION_GEN_CONSUMER_PREFIX = "voice-question-gen-consumer-";
}