package com.yun.aiinterview.service;

import cn.hutool.core.lang.UUID;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import com.yun.aiinterview.constant.AsyncTaskConstants;
import com.yun.aiinterview.context.BaseContext;
import com.yun.aiinterview.entity.KnowledgeBase;
import com.yun.aiinterview.entity.KnowledgeBaseQuestion;
import com.yun.aiinterview.handler.BusinessException;
import com.yun.aiinterview.mapper.KnowledgeBaseMapper;
import com.yun.aiinterview.mapper.KnowledgeBaseQuestionMapper;
import com.yun.aiinterview.pojo.dto.ConsumerConfig;
import com.yun.aiinterview.pojo.vo.AsyncTaskStatus;
import com.yun.aiinterview.pojo.vo.KnowledgeBaseQuestionVO;
import com.yun.aiinterview.pojo.vo.KnowledgeBaseVO;
import org.redisson.api.StreamMessageId;
import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.splitter.TextSplitter;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

import static com.yun.aiinterview.constant.AsyncTaskConstants.*;
import static com.yun.aiinterview.constant.AsyncTaskConstants.PENDING_CLAIM_BATCH_SIZE;
import static com.yun.aiinterview.constant.AsyncTaskConstants.PENDING_IDLE_TIMEOUT_MS;
import static com.yun.aiinterview.constant.AsyncTaskConstants.POLL_INTERVAL_MS;

@Service
@Slf4j
public class KnowledgeBaseService extends ServiceImpl<KnowledgeBaseMapper, KnowledgeBase> {

    /** 允许上传的知识库文件扩展名白名单 */
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(
            "pdf", "md", "markdown", "txt", "html", "htm",
            "doc", "docx", "ppt", "pptx", "xls", "xlsx");
    private static final int MAX_BATCH_SIZE = 10;
    private static final String METADATA_KB_ID = "kb_id";

    private final KnowledgeBaseMapper knowledgeBaseMapper;
    private final KnowledgeBaseQuestionMapper knowledgeBaseQuestionMapper;
    private final FileStorageService fileStorageService;
    private final FileService fileService;
    private final TextSplitter textSplitter = TokenTextSplitter.builder().build();
    private final VectorStore vectorStore;
    private final JdbcTemplate jdbcTemplate;
    private final AsyncTaskStreamService asyncTaskStreamService;
    private final AsyncStateService asyncStateService;

    private String consumerName;
    private static final String threadName ="kb-vector-consumer";

    public KnowledgeBaseService(KnowledgeBaseMapper knowledgeBaseMapper,
                                KnowledgeBaseQuestionMapper knowledgeBaseQuestionMapper,
                                FileStorageService fileStorageService,
                                FileService fileService,
                                @Qualifier("qaVectorStore") VectorStore vectorStore,
                                JdbcTemplate jdbcTemplate,
                                AsyncTaskStreamService asyncTaskStreamService,
                                AsyncStateService asyncStateService) {
        this.knowledgeBaseMapper = knowledgeBaseMapper;
        this.knowledgeBaseQuestionMapper = knowledgeBaseQuestionMapper;
        this.fileStorageService = fileStorageService;
        this.fileService = fileService;
        this.vectorStore = vectorStore;
        this.jdbcTemplate = jdbcTemplate;
        this.asyncTaskStreamService = asyncTaskStreamService;
        this.asyncStateService = asyncStateService;
    }

    /**
     * 上传知识库文件：校验 -> 去重 -> 存 MinIO -> 存库 -> 触发发布异步任务
     *
     * @param file 上传的文件
     * @return 入库后的知识库记录
     */
    @Transactional
    public KnowledgeBase upload(MultipartFile file) {
        // 1. 非空校验
        if (file == null || file.isEmpty()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "文件为空");
        }
        if (file.getSize() <= 0) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "文件内容为空");
        }

        // 2. 格式校验
        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || originalFilename.isBlank()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "文件名不能为空");
        }
        String extension = extensionOf(originalFilename);
        if (extension == null || !ALLOWED_EXTENSIONS.contains(extension)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "文件格式不支持，请上传 PDF、Word、Markdown 或 TXT 等文档格式");
        }

        // 3. 内容 Hash 去重校验
        String fileHash = fileService.calculateHash(file);
        Long existsCount = knowledgeBaseMapper.selectCount(
                new LambdaQueryWrapper<KnowledgeBase>()
                        .eq(KnowledgeBase::getFileHash, fileHash));
        if (existsCount != null && existsCount > 0) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "该知识库文件已上传过，请勿重复上传");
        }

        // 4. 存 MinIO，拿到存储 Key
        String storageKey = fileStorageService.uploadKnowledgeBase(file);

        // 5. 存库
        KnowledgeBase kb = new KnowledgeBase();
        kb.setFileHash(fileHash);
        kb.setName(stemOf(originalFilename));
        kb.setUserId(BaseContext.getCurrentId());
        kb.setOriginalFilename(originalFilename);
        kb.setFileSize(file.getSize());
        kb.setContentType(file.getContentType());
        kb.setStorageKey(storageKey);
        kb.setUploadedAt(LocalDateTime.now());
        kb.setVectorStatusUpdatedAt(LocalDateTime.now());
        save(kb);
        log.info("知识库文件入库成功: kbId={}, storageKey={}", kb.getId(), storageKey);

        String content = fileService.parseText(file);
        Long kbId = kb.getId();
        // 6. 触发发布异步任务（向量化等）
        //    必须在事务提交成功后投递消息，否则消费者可能先于提交读到不到记录而误报"知识库不存在"
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(
                    new TransactionSynchronization() {
                        @Override
                        public void afterCommit() {
                            sendGenerateTask(kbId, content);
                        }
                    });
        } else {
            sendGenerateTask(kbId, content);
        }

        return kb;
    }

    /**
     * 触发知识库发布异步任务（ 分块 -> 写入向量库 -> 更新 vector_status）
     */
    public void sendGenerateTask(Long kbId, String content) {
        sendGenerateTask(kbId, content, 0);
    }
    private void sendGenerateTask(Long kbId, String content, int retryCount) {
        Map<String, String> messages = Map.of(
                AsyncTaskConstants.FIELD_KB_ID, kbId.toString(),
                AsyncTaskConstants.FIELD_CONTENT, content,
                AsyncTaskConstants.FIELD_RETRY_COUNT, String.valueOf(retryCount)
        );
        try {
            String messageId = asyncTaskStreamService.sendTask(KB_VECTORIZE_STREAM_KEY, messages);
            log.info("知识库向量化任务已发送到Stream, messageId={}", messageId);
        } catch (Exception e) {
            asyncStateService.vectorMarkFailed(kbId);
            log.warn("入队失败后更新状态失败，可能任务已被覆盖或删除, kbId={}", kbId);

        }
    }
    @PostConstruct
    public void init(){
        consumerName = KB_VECTORIZE_CONSUMER_PREFIX
                + UUID.randomUUID().toString().substring(0, 8);
        asyncTaskStreamService.registerConsumer(
                new ConsumerConfig(
                        KB_VECTORIZE_STREAM_KEY,
                        KB_VECTORIZE_GROUP_NAME,
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
        String content = data.get(AsyncTaskConstants.FIELD_CONTENT);
        Long kbId = Long.parseLong(data.get(AsyncTaskConstants.FIELD_KB_ID));
        String retryCount = data.get(AsyncTaskConstants.FIELD_RETRY_COUNT);

        KnowledgeBase kb = getById(kbId);

        if (kb == null) {
            log.warn("知识库不存在，丢弃: messageId={}", messageId);
            // 确认消息
            asyncTaskStreamService.ackMessage(KB_VECTORIZE_STREAM_KEY, KB_VECTORIZE_GROUP_NAME, messageId);
            if(!asyncStateService.vectorMarkFailed(kbId)){
                return;
            }
        }
        try {
            if(!asyncStateService.vectorMarkProcessing(kbId)){
                log.warn("知识库向量化任务已被处理，丢弃: messageId={}", messageId);
                asyncTaskStreamService.ackMessage(KB_VECTORIZE_STREAM_KEY, KB_VECTORIZE_GROUP_NAME, messageId);
                return;
            }
            //开始向量化
            Integer totalChunks = vectorizeAndStore(kbId, content);
            // 更新状态为成功
            boolean ok = asyncStateService.vectorMarkCompleted(kbId, totalChunks);
            if (ok) {
                log.info("知识库向量化状态设置为完成: kbId={}, chunks={}", kbId, totalChunks);
            } else {
                log.info("向量化已完成或状态无效，跳过: kbId={}", kbId);
            }
        } catch (Exception e) {
            try{
                log.error("知识库向量化任务处理异常: messageId={}", messageId, e);
                if (Integer.parseInt(retryCount) >= AsyncTaskConstants.MAX_RETRY_COUNT) {
                    log.warn("知识库向量化任务重试次数超过最大重试次数，丢弃: messageId={}", messageId);
                    asyncStateService.vectorMarkFailed(kbId);
                } else {
                    sendGenerateTask(kbId, content, Integer.parseInt(retryCount) + 1);
                }
            }finally {
                asyncTaskStreamService.ackMessage(KB_VECTORIZE_STREAM_KEY, KB_VECTORIZE_GROUP_NAME, messageId);
            }

        }
    }

    private Integer vectorizeAndStore(Long knowledgeBaseId, String content){
        try {
            if (knowledgeBaseId == null) {
                throw new IllegalArgumentException("knowledgeBaseId不能为空");
            }

            KnowledgeBase kb = getById(knowledgeBaseId);
            if (kb == null) {
                throw new BusinessException(HttpStatus.NOT_FOUND, "知识库不存在: kbId=" + knowledgeBaseId);
            }
            if (AsyncTaskStatus.COMPLETED.equals(kb.getVectorStatus())) {
                // 幂等：已完成视为成功，返回已有 chunk 数或 0，不要抛异常
                log.info("知识库已向量化，跳过: kbId={}", knowledgeBaseId);
                return kb.getChunkCount();
            }

            log.info("开始向量化知识库: kbId={},  contentLength={}",
                    knowledgeBaseId,  content.length());

            // 1. 将文本分块
            List<Document> chunks = textSplitter.apply(
                    List.of(new Document(content))
            );

            log.info("文本分块完成: {} 个chunks", chunks.size());

            // 2. 为每个 chunk 添加临时 metadata，成功后再提升为正式 kb_id。
            for(Document chunk:chunks){
                chunk.getMetadata().put(METADATA_KB_ID, knowledgeBaseId.toString());
            }

            // 3. 分批向量化并存储（阿里云 DashScope API 限制 batch size <= 10）
            int totalChunks = chunks.size();
            int batchCount = (totalChunks + MAX_BATCH_SIZE - 1) / MAX_BATCH_SIZE; // 向上取整
            log.info("开始分批向量化: 总共 {} 个chunks，分 {} 批处理，每批最多 {} 个",
                    totalChunks, batchCount, MAX_BATCH_SIZE);
            for (int i = 0; i < batchCount; i++) {
                int start = i * MAX_BATCH_SIZE;
                int end = Math.min(start + MAX_BATCH_SIZE, totalChunks);
                List<Document> batch = chunks.subList(start, end);
                log.debug("处理第 {}/{} 批: chunks {}-{}", i + 1, batchCount, start + 1, end);
                vectorStore.add(batch);
            }

            log.info("知识库向量化完成: kbId={}, chunks={}, batches={}",
                    knowledgeBaseId, totalChunks, batchCount);
            return totalChunks;
        } catch (Exception e) {

            log.error("向量化知识库失败: kbId={}, error={}",
                    knowledgeBaseId,  e.getMessage(), e);
            throw new BusinessException(HttpStatus.BAD_REQUEST,
                    "向量化知识库失败: " + e.getMessage());
        }
    }

    /**
     * 提取文件扩展名（小写，不含点号）
     */
    private String extensionOf(String filename) {
        int index = filename.lastIndexOf('.');
        return index == -1 || index == filename.length() - 1
                ? null
                : filename.substring(index + 1).toLowerCase(Locale.ROOT);
    }

    /**
     * 提取文件名主干（去掉扩展名）
     */
    private String stemOf(String filename) {
        int index = filename.lastIndexOf('.');
        return index <= 0 ? filename : filename.substring(0, index);
    }

    /**
     * 查询知识库列表
     *
     * @return 知识库 VO 列表（按上传时间倒序）
     */
    public List<KnowledgeBaseVO> listKnowledgeBases() {
        Long userId = BaseContext.getCurrentId();
        List<KnowledgeBase> list = lambdaQuery()
                .eq(KnowledgeBase::getUserId, userId)
                .orderByDesc(KnowledgeBase::getUploadedAt)
                .list();
        return list.stream().map(this::toVO).toList();
    }

    /**
     * 根据知识库 ID 查询题库列表
     *
     * @param kbId 知识库 ID
     * @return 题目 VO 列表
     */
    public List<KnowledgeBaseQuestionVO> listQuestions(Long kbId) {
        return knowledgeBaseQuestionMapper.selectList(
                        new LambdaQueryWrapper<KnowledgeBaseQuestion>()
                                .eq(KnowledgeBaseQuestion::getKbId, kbId))
                .stream()
                .map(q -> new KnowledgeBaseQuestionVO(
                        q.getType(),
                        q.getQuestion(),
                        q.getTopicSummary(),
                        q.getReferenceAnswer(),
                        q.getScoringRubric()
                ))
                .toList();
    }

    /**
     * 删除知识库：删除关联题目 -> 删除向量数据 -> 删除 MinIO 文件 -> 删除主记录
     *
     * @param id 知识库 ID
     */
    @Transactional
    public void deleteKnowledgeBase(Long id) {
        KnowledgeBase kb = getById(id);
        if (kb == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "知识库不存在：" + id);
        }

        // 1. 删除关联的知识库问题
        knowledgeBaseQuestionMapper.delete(
                new LambdaQueryWrapper<KnowledgeBaseQuestion>()
                        .eq(KnowledgeBaseQuestion::getKbId, id));

        // 2. 删除向量数据（按 metadata 中的 kb_id 过滤），表名与 AiConfig 中配置的 vector_qa 保持一致
        String vectorTableName = "vector_qa";
        int vectorDeleted = jdbcTemplate.update(
                "DELETE FROM " + vectorTableName + " WHERE metadata ->> 'kb_id' = ?",
                id.toString());
        log.info("知识库删除向量完成: kbId={}, 删除向量数={}", id, vectorDeleted);

        // 3. 删除 MinIO 存储的知识库文件
        if (kb.getStorageKey() != null && !kb.getStorageKey().isBlank()) {
            fileStorageService.deleteKnowledgeBase(kb.getStorageKey());
        }

        // 4. 删除主记录
        removeById(id);
        log.info("知识库删除完成: kbId={}", id);
    }

    /**
     * 实体转 VO：文件大小格式化为可读字符串，时间格式化为 yyyy-MM-dd HH:mm:ss
     */
    private KnowledgeBaseVO toVO(KnowledgeBase kb) {
        return new KnowledgeBaseVO(
                kb.getId(),
                kb.getName(),
                formatFileSize(kb.getFileSize()),
                kb.getUploadedAt() == null ? null : kb.getUploadedAt().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")),
                kb.getVectorStatus(),
                kb.getQuestionGenStatus()
        );
    }

    /**
     * 将字节数格式化为可读的文件大小
     */
    private String formatFileSize(Long size) {
        if (size == null || size < 0) {
            return "0 B";
        }
        if (size < 1024) {
            return size + " B";
        }
        if (size < 1024L * 1024) {
            return String.format(Locale.ROOT, "%.1f KB", size / 1024.0);
        }
        if (size < 1024L * 1024 * 1024) {
            return String.format(Locale.ROOT, "%.1f MB", size / (1024.0 * 1024));
        }
        return String.format(Locale.ROOT, "%.1f GB", size / (1024.0 * 1024 * 1024));
    }

}