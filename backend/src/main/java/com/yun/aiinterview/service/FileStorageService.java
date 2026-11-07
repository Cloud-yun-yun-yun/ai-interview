package com.yun.aiinterview.service;

import cn.hutool.core.lang.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.yun.aiinterview.config.StorageConfigProperties;
import com.yun.aiinterview.handler.BusinessException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

import java.io.IOException;

@Service
@RequiredArgsConstructor
@Slf4j
public class FileStorageService {

    private final S3Client s3Client;                 // IOC 直接注入
    private final StorageConfigProperties storageConfig;

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
     * 上传知识库文件
     */
    public String uploadKnowledgeBase(MultipartFile file) {
        return uploadFile(file, "knowledgebases");
    }

    /**
     * 删除知识库文件
     */
    public void deleteKnowledgeBase(String fileKey) {
        deleteFile(fileKey);
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