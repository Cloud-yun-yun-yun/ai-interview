package com.yun.aiinterview.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import com.yun.aiinterview.config.ResourcesProperties;
import com.yun.aiinterview.entity.Resume;
import com.yun.aiinterview.entity.ResumeAnalysisResponse;
import com.yun.aiinterview.entity.InterviewSession;
import com.yun.aiinterview.handler.BusinessException;
import com.yun.aiinterview.mapper.InterviewSessionMapper;
import com.yun.aiinterview.mapper.ResumeAnalysisResponseMapper;
import com.yun.aiinterview.mapper.ResumeMapper;
import com.yun.aiinterview.pojo.dto.ConsumerConfig;
import com.yun.aiinterview.pojo.vo.AsyncTaskStatus;
import com.yun.aiinterview.pojo.vo.ResumeAnalysisVO;
import com.yun.aiinterview.pojo.vo.ResumeVO;
import org.redisson.api.StreamMessageId;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.core.io.ResourceLoader;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static com.yun.aiinterview.constant.AsyncTaskConstants.BATCH_SIZE;
import static com.yun.aiinterview.constant.AsyncTaskConstants.FIELD_RESUME_ID;
import static com.yun.aiinterview.constant.AsyncTaskConstants.FIELD_RETRY_COUNT;
import static com.yun.aiinterview.constant.AsyncTaskConstants.FIELD_USER_ID;
import static com.yun.aiinterview.constant.AsyncTaskConstants.MAX_RETRY_COUNT;
import static com.yun.aiinterview.constant.AsyncTaskConstants.PENDING_CLAIM_BATCH_SIZE;
import static com.yun.aiinterview.constant.AsyncTaskConstants.PENDING_IDLE_TIMEOUT_MS;
import static com.yun.aiinterview.constant.AsyncTaskConstants.POLL_INTERVAL_MS;
import static com.yun.aiinterview.constant.AsyncTaskConstants.RESUME_ANALYSIS_CONSUMER_PREFIX;
import static com.yun.aiinterview.constant.AsyncTaskConstants.RESUME_ANALYSIS_GROUP_NAME;
import static com.yun.aiinterview.constant.AsyncTaskConstants.RESUME_ANALYSIS_STREAM_KEY;
import static com.yun.aiinterview.entity.Resume.ACCEPT_INTERVIEW;

@Service
@Slf4j
public class ResumeService extends ServiceImpl<ResumeMapper, Resume> {

    private static final String CACHE_LOCK_PREFIX = "resume:analysis:lock:";

    /** 允许上传的简历文件扩展名白名单 */
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(
            "pdf", "md", "markdown", "txt", "html", "htm", "doc", "docx");

    private final ChatClient chatClient;
    private final FileService fileService;
    private final ResumeMapper resumeMapper;
    private final InterviewSessionMapper interviewSessionMapper;
    private final ResumeAnalysisResponseMapper analysisResponseMapper;
    private final PromptTemplate systemPromptTemplate;
    private final PromptTemplate userPromptTemplate;
    private final RedisService redisService;
    private final AsyncTaskStreamService asyncTaskStreamService;

    private static final String threadName = "resume-analysis-consumer";
    private String consumerName;

    public ResumeService(
            ChatClient chatClient,
            FileService fileService,
            ResumeMapper resumeMapper,
            InterviewSessionMapper interviewSessionMapper,
            ResumeAnalysisResponseMapper analysisResponseMapper,
            ResourcesProperties properties,
            ResourceLoader resourceLoader,
            RedisService redisService,
            AsyncTaskStreamService asyncTaskStreamService) throws IOException {
        this.chatClient = chatClient;
        this.fileService = fileService;
        this.resumeMapper = resumeMapper;
        this.interviewSessionMapper = interviewSessionMapper;
        this.analysisResponseMapper = analysisResponseMapper;
        this.systemPromptTemplate = new PromptTemplate(
                resourceLoader.getResource(properties.getResumeAnalysisSystem())
                        .getContentAsString(StandardCharsets.UTF_8)
        );
        this.userPromptTemplate = new PromptTemplate(
                resourceLoader.getResource(properties.getResumeAnalysisUser())
                        .getContentAsString(StandardCharsets.UTF_8)
        );
        this.redisService = redisService;
        this.asyncTaskStreamService = asyncTaskStreamService;
    }

    public void uploadAndParse(Long userId, MultipartFile file) {
        // 1. 文件非空校验
        if (file.isEmpty()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "文件为空");
        }

        // 2. 格式校验：仅允许常规文本/文档类简历格式
        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null
                || originalFilename.lastIndexOf('.') == -1
                || !ALLOWED_EXTENSIONS.contains(extensionOf(originalFilename))) {
            throw new BusinessException(HttpStatus.BAD_REQUEST,
                    "文件格式不支持，请上传 PDF、Word、Markdown、TXT 或 HTML 格式的简历");
        }

        // 3. 内容 Hash 去重校验
        String fileHash = fileService.calculateHash(file);
        Long existsCount = resumeMapper.selectCount(
                new LambdaQueryWrapper<Resume>()
                        .eq(Resume::getFileHash, fileHash)
                        .eq(Resume::getUserId, userId));
        if (existsCount != null && existsCount > 0) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "该简历已上传过，请勿重复上传");
        }

        // 3. 简历名称
        String name = originalFilename.substring(0, originalFilename.lastIndexOf('.'));
        if (name.isBlank()) {
            name = originalFilename;
        }

        // 4. 文本解析
        String text = fileService.parseText(file);
        if (text == null || text.isBlank()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "未能从 文件 中提取到文本，可能是扫描件或文件损坏");
        }
        int len = text.length();
        if (len > Resume.MAX_TEXT_LENGTH) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "文本过长（" + len + " 字符），超过单页上限");
        }
        Resume resume = Resume.builder()
                .userId(userId)
                .content(text)
                .textLength(len)
                .asyncTaskStatus(AsyncTaskStatus.PENDING)
                .name(name)
                .fileHash(fileHash)
                .interviewStatus(ACCEPT_INTERVIEW)
                .build();
        resumeMapper.insert(resume);
        log.info("简历上传+解析完成，文本长度={}", len);
    }

    /** 提取文件扩展名（小写，不含点） */
    private static String extensionOf(String filename) {
        return filename.substring(filename.lastIndexOf('.') + 1)
                .toLowerCase();
    }

    /**
     * 提交简历分析（异步）：
     * 在分布式锁（粒度=简历 id）保护下，若已有分析结果则直接返回 COMPLETED，
     * 否则将分析任务投递到 Stream 异步执行。
     */
    public ResumeAnalysisVO analysisResume(Long resumeId, Long userId) {
        String lockKey = CACHE_LOCK_PREFIX + resumeId;
        return redisService.executeWithLock(lockKey, 10, TimeUnit.SECONDS, () -> {
            // 1. 查询数据库确认是否已有分析结果
            ResumeAnalysisResponse dbResponse = analysisResponseMapper.selectOne(
                    new LambdaQueryWrapper<ResumeAnalysisResponse>()
                            .eq(ResumeAnalysisResponse::getResumeId, resumeId)
                            .eq(ResumeAnalysisResponse::getUserId, userId));
            if (dbResponse != null) {
                log.info("简历已有分析结果，直接返回，resumeId={}", resumeId);
                return new ResumeAnalysisVO(resumeId, AsyncTaskStatus.COMPLETED);
            }

            // 2. 校验简历存在及归属
            Resume resume = resumeMapper.selectById(resumeId);
            if (resume == null) {
                throw new BusinessException(HttpStatus.NOT_FOUND, "简历不存在：" + resumeId);
            }
            if (!resume.getUserId().equals(userId)) {
                throw new BusinessException(HttpStatus.FORBIDDEN, "无权分析他人的简历");
            }

            // 3. 已处理中，避免重复提交
            if (resume.getAsyncTaskStatus() == AsyncTaskStatus.PROCESSING) {
                log.info("简历正在分析中，resumeId={}", resumeId);
                return new ResumeAnalysisVO(resumeId, AsyncTaskStatus.PROCESSING);
            }

            // 4. 置为排队中，投递异步任务
            resume.setAsyncTaskStatus(AsyncTaskStatus.PENDING);
            resumeMapper.updateById(resume);
            sendAnalysisTask(resumeId, userId);
            log.info("简历分析任务已提交，resumeId={}", resumeId);
            return new ResumeAnalysisVO(resumeId, AsyncTaskStatus.PENDING);
        });
    }

    /**
     * 投递简历分析异步任务到 Stream，参考知识库向量化的异步执行方式
     */
    private void sendAnalysisTask(Long resumeId, Long userId) {
        sendAnalysisTask(resumeId, userId, 0);
    }

    private void sendAnalysisTask(Long resumeId, Long userId, int retryCount) {
        Map<String, String> messages = Map.of(
                FIELD_RESUME_ID, resumeId.toString(),
                FIELD_USER_ID, userId.toString(),
                FIELD_RETRY_COUNT, String.valueOf(retryCount)
        );
        try {
            asyncTaskStreamService.sendTask(RESUME_ANALYSIS_STREAM_KEY, messages);
            log.info("简历分析任务已发送到Stream, resumeId={}", resumeId);
        } catch (Exception e) {
            log.warn("简历分析任务入队失败，更新为失败状态, resumeId={}", resumeId, e);
            updateResumeStatus(resumeId, AsyncTaskStatus.FAILED);
        }
    }

    @PostConstruct
    public void init() {
        this.consumerName = RESUME_ANALYSIS_CONSUMER_PREFIX
                + UUID.randomUUID().toString().substring(0, 8);
        asyncTaskStreamService.registerConsumer(
                new ConsumerConfig(
                        RESUME_ANALYSIS_STREAM_KEY,
                        RESUME_ANALYSIS_GROUP_NAME,
                        consumerName,
                        threadName,
                        BATCH_SIZE,
                        POLL_INTERVAL_MS,
                        PENDING_IDLE_TIMEOUT_MS,
                        PENDING_CLAIM_BATCH_SIZE
                ),
                this::processMessage
        );
    }

    private void processMessage(StreamMessageId messageId, Map<String, String> data) {
        String resumeIdStr = data.get(FIELD_RESUME_ID);
        String userIdStr = data.get(FIELD_USER_ID);
        String retryCountStr = data.get(FIELD_RETRY_COUNT);
        if (resumeIdStr == null || userIdStr == null) {
            log.warn("简历分析消息格式错误，丢弃: messageId={}", messageId);
            asyncTaskStreamService.ackMessage(RESUME_ANALYSIS_STREAM_KEY, RESUME_ANALYSIS_GROUP_NAME, messageId);
            return;
        }
        Long resumeId = Long.parseLong(resumeIdStr);
        Long userId = Long.parseLong(userIdStr);
        try {
            Resume resume = resumeMapper.selectById(resumeId);
            if (resume == null) {
                log.warn("简历不存在，丢弃: messageId={}", messageId);
                asyncTaskStreamService.ackMessage(RESUME_ANALYSIS_STREAM_KEY, RESUME_ANALYSIS_GROUP_NAME, messageId);
                return;
            }

            // 幂等：如果已完成，直接 ACK 跳过
            if (AsyncTaskStatus.COMPLETED.equals(resume.getAsyncTaskStatus())) {
                log.info("简历分析已完成，跳过重复消息: resumeId={}, messageId={}", resumeId, messageId);
                asyncTaskStreamService.ackMessage(RESUME_ANALYSIS_STREAM_KEY, RESUME_ANALYSIS_GROUP_NAME, messageId);
                return;
            }

            // 置为处理中
            updateResumeStatus(resumeId, AsyncTaskStatus.PROCESSING);

            // 执行 AI 分析
            ResumeAnalysisResponse response = doAnalysis(resume, userId);

            // 1. 分析结果写入数据库
            response.setResumeId(resumeId);
            response.setUserId(userId);
            analysisResponseMapper.insert(response);
            log.info("简历分析结果已写入数据库，resumeId={}", resumeId);

            // 2. 更新状态为完成
            updateResumeStatus(resumeId, AsyncTaskStatus.COMPLETED);

            // 3. 确认消息
            asyncTaskStreamService.ackMessage(RESUME_ANALYSIS_STREAM_KEY, RESUME_ANALYSIS_GROUP_NAME, messageId);
        } catch (Exception e) {
            log.error("简历分析失败，resumeId={}", resumeId, e);
            int retryCount = retryCountStr == null ? 0 : Integer.parseInt(retryCountStr);
            if (retryCount >= MAX_RETRY_COUNT) {
                log.warn("简历分析重试次数超过最大重试次数，丢弃: messageId={}", messageId);
                updateResumeStatus(resumeId, AsyncTaskStatus.FAILED);
            } else {
                sendAnalysisTask(resumeId, userId, retryCount + 1);
            }
            asyncTaskStreamService.ackMessage(RESUME_ANALYSIS_STREAM_KEY, RESUME_ANALYSIS_GROUP_NAME, messageId);
        }
    }

    private void updateResumeStatus(Long resumeId, AsyncTaskStatus status) {
        resumeMapper.update(null,
                new LambdaUpdateWrapper<Resume>()
                        .eq(Resume::getId, resumeId)
                        .set(Resume::getAsyncTaskStatus, status));
    }

    private ResumeAnalysisResponse doAnalysis(Resume resume, Long userId) {
        String text = resume.getContent();
        log.info("开始分析简历，resumeId={}, 文本长度: {} 字符", resume.getId(), text.length());
        try {
            log.info("加载提示词");
            // 1. 读取 system / user 提示词
            String system = systemPromptTemplate.getTemplate();
            // 2. 准备变量
            Map<String, Object> variables = new HashMap<>();
            variables.put("resumeText", text);
            // 3. 渲染用户提示词，替换 {resumeText}
            String user = userPromptTemplate.render(variables);
            log.info("提示词加载完毕，开始调用模型");
            // 4. 调用 ChatClient，system + 渲染后的 user，返回结构化对象
            ResumeAnalysisResponse response = chatClient.prompt()
                    .system(system)
                    .user(user)
                    .call()
                    .entity(ResumeAnalysisResponse.class);
            // 清空 ID，确保数据库自动生成
            response.setId(null);
            log.info("分析简历完成，准备写入数据库");
            // 兜底：对 AI 输出做字段级校验，保证后续落库/写缓存的数据结构完整、数值合法
            sanitizeAnalysisResponse(response);
            return response;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("简历分析失败，resumeId={}", resume.getId(), e);
            throw new BusinessException(HttpStatus.INTERNAL_SERVER_ERROR, "简历分析失败，请稍后重试");
        }
    }

    @Transactional
    public void deleteResume(Long resumeId, Long userId) {
        // 1. 归属校验：只能删除自己的简历
        Resume resume = resumeMapper.selectById(resumeId);
        if (resume == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "简历不存在：" + resumeId);
        }
        if (!resume.getUserId().equals(userId)) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "无权删除他人的简历");
        }

        // 2. 约束校验：存在"进行中（CREATED）"的面试会话时，禁止删除
        Long unfinishedCount = interviewSessionMapper.selectCount(
                new LambdaQueryWrapper<InterviewSession>()
                        .eq(InterviewSession::getResumeId, resumeId)
                        .eq(InterviewSession::getStatus, InterviewSession.STATUS_CREATED));
        if (unfinishedCount != null && unfinishedCount > 0) {
            throw new BusinessException(HttpStatus.BAD_REQUEST,
                    "该简历存在进行中的面试，请完成考试后再删除");
        }

        // 3. 删除简历分析报告
        analysisResponseMapper.delete(
                new LambdaQueryWrapper<ResumeAnalysisResponse>()
                        .eq(ResumeAnalysisResponse::getResumeId, resumeId));

        // 4. 删除简历本身（已完成考试的会话记录保留不动）
        resumeMapper.deleteById(resumeId);
    }

    /**
     * 兜底：保证 AI 输出的结构化结果数据结构完整、数值合法、永不为 null 列表，
     */
    private void sanitizeAnalysisResponse(ResumeAnalysisResponse r) {
        if (r == null) {
            throw new BusinessException(HttpStatus.INTERNAL_SERVER_ERROR, "AI 分析结果为空");
        }
        boolean fallback = false;
        // 1. 分数钳制到 [0, 100]
        r.setOverallScore(clampScore(r.getOverallScore()));
        r.setProjectScore(clampScore(r.getProjectScore()));
        r.setSkillMatchScore(clampScore(r.getSkillMatchScore()));
        r.setContentScore(clampScore(r.getContentScore()));
        r.setStructureScore(clampScore(r.getStructureScore()));
        r.setExpressionScore(clampScore(r.getExpressionScore()));
        // 2. 总评兜底，避免 null / 空白
        if (r.getSummary() == null || r.getSummary().isBlank()) {
            r.setSummary("该简历暂未生成综合评价，请结合具体分数与建议参考。");
            fallback = true;
        }
        // 3. 建议列表保底：非 null 且至少一条；逐条字段补默认值
        List<ResumeAnalysisResponse.Suggestion> suggestions = r.getSuggestions();
        if (suggestions == null || suggestions.isEmpty()) {
            r.setSuggestions(List.of(buildFallbackSuggestion()));
            fallback = true;
        } else {
            for (ResumeAnalysisResponse.Suggestion s : suggestions) {
                if (sanitizeSuggestion(s)) {
                    fallback = true;
                }
            }
            r.setSuggestions(suggestions);
        }
        // 4. 兜底被触发时标记允许再次解析，让用户有机会重新调 AI 获得更完整结果
        r.setAllowReparse(fallback);
    }

    private int clampScore(int score) {
        if (score < 0) {
            return 0;
        }
        return Math.min(score, 100);
    }

    private boolean sanitizeSuggestion(ResumeAnalysisResponse.Suggestion s) {
        boolean fixed = false;

        if (s.getCategory() == null || s.getCategory().isBlank()) {
            s.setCategory("通用建议");
            fixed = true;
        }
        if (s.getPriority() == null || s.getPriority().isBlank()) {
            s.setPriority("中");
            fixed = true;
        }
        if (s.getIssue() == null || s.getIssue().isBlank()) {
            s.setIssue("待补充说明");
            fixed = true;
        }
        if (s.getRecommendation() == null || s.getRecommendation().isBlank()) {
            s.setRecommendation("建议补充具体改进方向");
            fixed = true;
        }
        return fixed;
    }

    private ResumeAnalysisResponse.Suggestion buildFallbackSuggestion() {
        ResumeAnalysisResponse.Suggestion s = new ResumeAnalysisResponse.Suggestion();
        s.setCategory("通用建议");
        s.setPriority("中");
        s.setIssue("简历内容有待完善");
        s.setRecommendation("建议补充项目经历与量化成果，突出个人技能匹配度。");
        return s;
    }


    public ResumeAnalysisResponse getAnalysisResponse(Long resumeId, Long userId) {
        Resume resume = resumeMapper.selectOne(
                new LambdaQueryWrapper<Resume>()
                        .eq(Resume::getId, resumeId)
                        .eq(Resume::getUserId, userId));
        if (resume == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "简历不存在：" + resumeId);
        }
        ResumeAnalysisResponse response = analysisResponseMapper.selectOne(
                new LambdaQueryWrapper<ResumeAnalysisResponse>()
                        .eq(ResumeAnalysisResponse::getResumeId, resumeId)
                        .orderByDesc(ResumeAnalysisResponse::getId)
                        .last("LIMIT 1"));
        if (response == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "该简历尚未生成分析报告");
        }
        return response;
    }

    public AsyncTaskStatus getResumeStatus(Long resumeId, Long userId) {
        Resume resume = resumeMapper.selectOne(
                new LambdaQueryWrapper<Resume>()
                        .eq(Resume::getId, resumeId)
                        .eq(Resume::getUserId, userId));
        if (resume == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "简历不存在：" + resumeId);
        }
        return resume.getAsyncTaskStatus();
    }

    public List<ResumeAnalysisVO> getResumeStatusBatch(List<Long> resumeIds, Long userId) {
        if (resumeIds == null || resumeIds.isEmpty()) {
            return List.of();
        }
        List<Resume> resumes = resumeMapper.selectList(
                new LambdaQueryWrapper<Resume>()
                        .in(Resume::getId, resumeIds)
                        .eq(Resume::getUserId, userId));
        return resumes.stream()
                .map(r -> new ResumeAnalysisVO(r.getId(), r.getAsyncTaskStatus()))
                .collect(Collectors.toList());
    }

    public List<ResumeAnalysisVO> listResumeStatus(Long userId) {
        return resumeMapper.selectList(
                        new LambdaQueryWrapper<Resume>()
                                .eq(Resume::getUserId, userId)
                                .orderByDesc(Resume::getId))
                .stream()
                .map(r -> new ResumeAnalysisVO(r.getId(), r.getAsyncTaskStatus()))
                .collect(Collectors.toList());
    }

    public List<ResumeVO> listResumes(Long userId) {
        return resumeMapper.selectList(
                        new LambdaQueryWrapper<Resume>()
                                .eq(Resume::getUserId, userId)
                                .orderByDesc(Resume::getId))
                .stream()
                .map(r -> new ResumeVO(r.getName(), r.getId(), r.getUserId(), r.getTextLength(), r.getInterviewStatus(), r.getAsyncTaskStatus()))
                .collect(Collectors.toList());
    }

}