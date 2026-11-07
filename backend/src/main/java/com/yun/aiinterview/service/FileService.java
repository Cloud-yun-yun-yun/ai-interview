package com.yun.aiinterview.service;

import com.yun.aiinterview.handler.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.sax.BodyContentHandler;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * 文件处理服务：哈希计算与文本解析，统一交由 Spring 管理
 */
@Service
@Slf4j
public class FileService {

    private static final String HASH_ALGORITHM = "SHA-256";

    /* ==================== 哈希计算 ==================== */

    public String calculateHash(MultipartFile file) {
        try {
            return calculateHash(file.getBytes());
        } catch (IOException e) {
            log.error("读取文件内容失败", e);
            throw new BusinessException(HttpStatus.INTERNAL_SERVER_ERROR, "计算文件哈希失败");
        }
    }

    public String calculateHash(byte[] data) {
        try {
            MessageDigest digest = MessageDigest.getInstance(HASH_ALGORITHM);
            //计算出字节数组（32 字节，二进制）。
            byte[] hashBytes = digest.digest(data);
            return bytesToHex(hashBytes);
        } catch (NoSuchAlgorithmException e) {
            log.error("哈希算法不支持: {}", HASH_ALGORITHM, e);
            throw new BusinessException(HttpStatus.INTERNAL_SERVER_ERROR, "计算文件哈希失败");
        }
    }

    //  将字节数组转换为十六进制字符串
    private String bytesToHex(byte[] bytes) {
        StringBuilder result = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            result.append(String.format("%02x", b));
        }
        return result.toString();
    }

    /* ==================== 文本解析 ==================== */

    public String parseText(MultipartFile file) {
        try {
            return parse(file.getInputStream(), file.getOriginalFilename());
        } catch (IOException e) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "文件解析失败");
        }
    }

    /**
     * 解析字节流（例如从 MinIO 下载的文件内容）
     *
     * @param bytes    文件字节内容
     * @param filename 原始文件名（用于 Tika 识别文件类型，如 xxx.pdf）
     */
    public String parseText(byte[] bytes, String filename) {
        return parse(new ByteArrayInputStream(bytes), filename);
    }

    private String parse(InputStream inputStream, String filename) {
        // 1. 创建自动检测解析器（会按文件头 magic bytes 自动识别类型）
        AutoDetectParser parser = new AutoDetectParser();

        // 2. 准备接收器（-1 表示不限制字符数）
        BodyContentHandler handler = new BodyContentHandler(-1);

        // 3. 准备元数据容器
        Metadata metadata = new Metadata();
        // filename 可空：有则作为类型识别的辅助 hint，无则由 AutoDetectParser 自动嗅探
        if (filename != null && !filename.isBlank()) {
            metadata.set(TikaCoreProperties.RESOURCE_NAME_KEY, filename);
        }
        try (TikaInputStream stream = TikaInputStream.get(inputStream)) {

            // 4. 解析：自动识别 -> 调用对应解析器 -> 内容写入 handler
            parser.parse(stream, handler, metadata);

            // 5. 直接拿到文本并返回
            return handler.toString();

        } catch (Exception e) {
            throw new BusinessException(HttpStatus.INTERNAL_SERVER_ERROR, "解析文件失败: " + filename);
        }
    }
}