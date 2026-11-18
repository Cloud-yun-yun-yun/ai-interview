package com.yun.aiinterview.service;

import cn.hutool.core.lang.UUID;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.yun.aiinterview.config.StorageConfigProperties;
import com.yun.aiinterview.handler.BusinessException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;

import java.io.IOException;
import java.net.URI;
import java.time.Duration;

@Service
@RequiredArgsConstructor
@Slf4j
public class FileStorageService {

    private final S3Client s3Client;                 // IOC 直接注入
    private final StorageConfigProperties storageConfig;

    /**
     * 应用启动时校验并自动创建 bucket。
     * 若 MinIO 中不存在配置的 bucket 则创建，随后应用可正常读写；
     * 可通过 minio.storage.autoCreateBucket=false 关闭自动创建。
     */
    @PostConstruct
    public void init() {
        if (!storageConfig.isAutoCreateBucket()) {
            log.info("autoCreateBucket=false，跳过 bucket 自动创建检查");
            return;
        }
        String bucket = storageConfig.getBucket();
        try {
            HeadBucketRequest headRequest = HeadBucketRequest.builder()
                    .bucket(bucket)
                    .build();
            s3Client.headBucket(headRequest);
            log.info("bucket 已存在: {}", bucket);
        } catch (NoSuchBucketException e) {
            try {
                CreateBucketRequest createRequest = CreateBucketRequest.builder()
                        .bucket(bucket)
                        .build();
                s3Client.createBucket(createRequest);
                log.info("bucket 自动创建成功: {}", bucket);
            } catch (S3Exception ex) {
                log.error("bucket 创建失败: {}", ex.getMessage(), ex);
            }
        } catch (S3Exception e) {
            log.warn("检查 bucket 状态失败: {}", e.getMessage());
        }
    }

    /**
     * 上传简历文件
     */
    public String uploadResume(MultipartFile file) {
        return uploadFile(file, "resumes");
    }

    /**
     * 删除简历文件
     */
    public void deleteResume(String fileKey) {
        deleteFile(fileKey);
    }

    /**
     * 上传知识库文件到结构化目录。
     * <p>与语音面试 key 规范保持一致：
     * key 形如 knowledgebase/{userId}/{uuid}_{filename}
     *
     * @param file   上传的文件
     * @param prefix 结构化前缀，如 "knowledgebase/1"
     * @return 完整文件 key
     */
    public String uploadKnowledgeBase(MultipartFile file, String prefix) {
        return uploadFile(file, prefix);
    }

    /**
     * 删除知识库文件
     */
    public void deleteKnowledgeBase(String fileKey) {
        deleteFile(fileKey);
    }

    /**
     * 上传音频文件（复用通用字节流上传，TTS 默认 mp3）。
     *
     * @param data     音频字节
     * @return 文件 key
     */
    public String uploadAudio(byte[] data) {
        return uploadBytes(data,"", "voices", "audio/mpeg");
    }

    /**
     * 上传用户回答音频（保留原始扩展名，便于 ASR 推断音频格式）。
     *
     * @param data             音频字节
     * @param originalFilename 原始文件名（用于推断格式与扩展名）
     * @return 文件 key
     */
    public String uploadUserAudio(byte[] data, String originalFilename) {
        return uploadBytes(data, originalFilename, "voices", resolveAudioContentType(originalFilename));
    }

    private String resolveAudioContentType(String filename) {
        if (filename == null) {
            return "audio/webm";
        }
        String lower = filename.toLowerCase();
        if (lower.endsWith(".wav")) return "audio/wav";
        if (lower.endsWith(".mp3")) return "audio/mpeg";
        if (lower.endsWith(".m4a")) return "audio/mp4";
        if (lower.endsWith(".ogg")) return "audio/ogg";
        return "audio/webm";
    }

    /**
     * 上传音频到指定 fileKey（幂等场景使用，覆盖同名文件）。
     *
     * @param data    音频字节
     * @param fileKey 固定存储 key，如 "voices/intro.mp3"
     */
    public void uploadAudioTo(byte[] data, String fileKey) {
        try {
            PutObjectRequest putRequest = PutObjectRequest.builder()
                    .bucket(storageConfig.getBucket())
                    .key(fileKey)
                    .contentType("audio/mpeg")
                    .contentLength((long) data.length)
                    .build();
            s3Client.putObject(putRequest, RequestBody.fromBytes(data));
            log.info("音频指定 key 上传成功: {}", fileKey);
        } catch (S3Exception e) {
            log.error("上传音频到MinIo失败: {}", e.getMessage(), e);
            throw new BusinessException(HttpStatus.BAD_REQUEST, "音频文件存储失败: " + e.getMessage());
        }
    }

    /**
     * 通用文件上传方法
     */
    private String uploadFile(MultipartFile file, String prefix) {
        String originalFilename = file.getOriginalFilename();
        String fileKey = prefix + "/" + UUID.randomUUID() + "_" + originalFilename;

        try {
            PutObjectRequest putRequest = PutObjectRequest.builder()
                    .bucket(storageConfig.getBucket())
                    .key(fileKey)
                    .contentType(file.getContentType())
                    .contentLength(file.getSize())
                    .build();

            s3Client.putObject(putRequest, RequestBody.fromInputStream(file.getInputStream(), file.getSize()));
            log.info("文件上传成功: {} -> {}", originalFilename, fileKey);
            return fileKey;
        } catch (IOException e) {
            log.error("读取上传文件失败: {}", e.getMessage(), e);
            throw new BusinessException(HttpStatus.BAD_REQUEST, "文件读取失败");
        } catch (S3Exception e) {
            log.error("上传文件到MinIo失败: {}", e.getMessage(), e);
            throw new BusinessException(HttpStatus.BAD_REQUEST, "文件存储失败: " + e.getMessage());
        }
    }

    /**
     * 通用上传字节流方法（任意内容类型）。
     *
     * @param data        文件字节内容
     * @param fileName    文件名（可带扩展名，用于生成存储 key）
     * @param prefix      存储目录前缀，如 "voices" / "images"
     * @param contentType 文件的 MIME 类型，如 "audio/mpeg" / "application/pdf"
     * @return 文件 key
     */
    public String uploadBytes(byte[] data, String fileName, String prefix, String contentType) {
        String safeName = (fileName == null || fileName.isBlank())
                ? "file_" + UUID.randomUUID()
                : UUID.randomUUID() + "_" + fileName;
        String fileKey = (prefix == null || prefix.isBlank() ? "files" : prefix) + "/" + safeName;

        try {
            PutObjectRequest putRequest = PutObjectRequest.builder()
                    .bucket(storageConfig.getBucket())
                    .key(fileKey)
                    .contentType(contentType)
                    .contentLength((long) data.length)
                    .build();
            s3Client.putObject(putRequest, RequestBody.fromBytes(data));
            log.info("字节流上传成功: {}", fileKey);
            return fileKey;
        } catch (S3Exception e) {
            log.error("上传字节流到MinIo失败: {}", e.getMessage(), e);
            throw new BusinessException(HttpStatus.BAD_REQUEST, "文件存储失败: " + e.getMessage());
        }
    }

    /**
     * 检查文件是否存在
     */
    public boolean fileExists(String fileKey) {
        try {
            HeadObjectRequest headRequest = HeadObjectRequest.builder()
                    .bucket(storageConfig.getBucket())
                    .key(fileKey)
                    .build();
            s3Client.headObject(headRequest);
            return true;
        } catch (NoSuchKeyException e) {
            return false;
        } catch (S3Exception e) {
            log.warn("检查文件存在性失败: {} - {}", fileKey, e.getMessage());
            return false;
        }
    }

    /**
     * 获取文件大小（字节）
     */
    public long getFileSize(String fileKey) {
        try {
            HeadObjectRequest headRequest = HeadObjectRequest.builder()
                    .bucket(storageConfig.getBucket())
                    .key(fileKey)
                    .build();
            return s3Client.headObject(headRequest).contentLength();
        } catch (S3Exception e) {
            log.error("获取文件大小失败: {} - {}", fileKey, e.getMessage());
            throw new BusinessException(HttpStatus.BAD_REQUEST, "获取文件大小失败");
        }
    }

    /**
     * 生成预签名下载 URL，固定 2 分钟有效期，前端可直接用于 &lt;audio&gt; 播放。
     *
     * @param fileKey 文件 key，如 "voices/xxx.mp3"
     * @return 预签名 URL
     */
    public String generatePresignedUrl(String fileKey) {
        return generatePresignedUrl(fileKey, Duration.ofMinutes(2));
    }

    /**
     * 生成预签名下载 URL，前端可直接用于 &lt;audio&gt; 播放。
     *
     * @param fileKey 文件 key，如 "voices/xxx.mp3"
     * @param expiry  有效期，如 Duration.ofMinutes(10)
     * @return 预签名 URL
     */
    public String generatePresignedUrl(String fileKey, Duration expiry) {
        GetObjectRequest getObjectRequest = GetObjectRequest
                .builder()
                .bucket(storageConfig.getBucket())
                .key(fileKey)
                .build();
        GetObjectPresignRequest presignRequest = GetObjectPresignRequest
                .builder()
                .signatureDuration(expiry)
                .getObjectRequest(getObjectRequest)
                .build();
        try (S3Presigner presigner = S3Presigner.builder()
                .endpointOverride(URI.create(storageConfig.getEndpoint()))
                .region(Region.of(storageConfig.getRegion()))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(storageConfig.getAccessKey(), storageConfig.getSecretKey())))
                // 必须与 S3Client 保持一致：MinIO 用路径风格，否则生成的 URL 形如
                // http://<bucket>.localhost:9000/xxx，浏览器请求会得到 NoSuchBucket
                .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build())
                .build()) {
            PresignedGetObjectRequest presigned = presigner.presignGetObject(presignRequest);
            return presigned.url().toString();
        }
    }

    /**
     * 上传音频到指定的完整 key（结构化路径，供按会话删除）。
     *
     * @param data        音频字节
     * @param fileKey     完整存储 key
     * @param contentType MIME 类型
     * @return fileKey
     */
    public String uploadTo(byte[] data, String fileKey, String contentType) {
        try {
            PutObjectRequest putRequest = PutObjectRequest.builder()
                    .bucket(storageConfig.getBucket())
                    .key(fileKey)
                    .contentType(contentType)
                    .contentLength((long) data.length)
                    .build();
            s3Client.putObject(putRequest, RequestBody.fromBytes(data));
            log.info("字节流指定 key 上传成功: {}", fileKey);
            return fileKey;
        } catch (S3Exception e) {
            log.error("上传字节流到MinIo失败: {}", e.getMessage(), e);
            throw new BusinessException(HttpStatus.BAD_REQUEST, "文件存储失败: " + e.getMessage());
        }
    }

    /**
     * 按前缀删除对象（如整租语音面试会话对应目录下所有音频）。
     *
     * @param prefix 对象 key 前缀，如 "ai-interview/voiceInterview/{userId}/{sessionId}"
     */
    public void deleteByPrefix(String prefix) {
        if (prefix == null || prefix.isEmpty()) {
            log.debug("前缀为空，跳过删除");
            return;
        }
        try {
            String continuationToken = null;
            do {
                ListObjectsV2Request.Builder listReq = ListObjectsV2Request.builder()
                        .bucket(storageConfig.getBucket())
                        .prefix(prefix);
                if (continuationToken != null) {
                    listReq.continuationToken(continuationToken);
                }
                ListObjectsV2Response resp = s3Client.listObjectsV2(listReq.build());
                for (S3Object obj : resp.contents()) {
                    DeleteObjectRequest delReq = DeleteObjectRequest.builder()
                            .bucket(storageConfig.getBucket())
                            .key(obj.key())
                            .build();
                    s3Client.deleteObject(delReq);
                    log.info("按前缀删除文件: {}", obj.key());
                }
                continuationToken = resp.isTruncated() ? resp.nextContinuationToken() : null;
            } while (continuationToken != null);
        } catch (S3Exception e) {
            log.error("按前缀删除失败: {}", e.getMessage(), e);
            throw new BusinessException(HttpStatus.BAD_REQUEST, "按前缀删除失败: " + e.getMessage());
        }
    }

    /**
     * 通用文件删除方法
     */
    private void deleteFile(String fileKey) {
        // 空键直接跳过
        if (fileKey == null || fileKey.isEmpty()) {
            log.debug("文件键为空，跳过删除");
            return;
        }

        // 检查文件是否存在，避免不必要的删除操作
        if (!fileExists(fileKey)) {
            log.warn("文件不存在，跳过删除: {}", fileKey);
            return;
        }

        try {
            DeleteObjectRequest deleteRequest = DeleteObjectRequest.builder()
                    .bucket(storageConfig.getBucket())
                    .key(fileKey)
                    .build();
            s3Client.deleteObject(deleteRequest);
            log.info("文件删除成功: {}", fileKey);
        } catch (S3Exception e) {
            log.error("删除文件失败: {} - {}", fileKey, e.getMessage(), e);
            throw new BusinessException(HttpStatus.BAD_REQUEST, "文件删除失败: " + e.getMessage());
        }
    }

    /**
     * 下载文件（通用方法）
     *
     * @param fileKey 文件存储键
     * @return 文件字节数组
     */
    public byte[] downloadFile(String fileKey) {
        if (!fileExists(fileKey)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "文件不存在: " + fileKey);
        }
        try {
            GetObjectRequest getRequest = GetObjectRequest.builder()
                    .bucket(storageConfig.getBucket())
                    .key(fileKey)
                    .build();
            return s3Client.getObjectAsBytes(getRequest).asByteArray();
        } catch (S3Exception e) {
            log.error("下载文件失败: {} - {}", fileKey, e.getMessage(), e);
            throw new BusinessException(HttpStatus.BAD_REQUEST, "文件下载失败: " + e.getMessage());
        }
    }
}