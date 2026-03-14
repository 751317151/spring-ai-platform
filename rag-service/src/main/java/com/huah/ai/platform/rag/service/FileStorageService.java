package com.huah.ai.platform.rag.service;

import java.io.InputStream;

/**
 * 文件存储服务接口（S3/MinIO/OSS）
 */
public interface FileStorageService {

    /**
     * 上传文件
     * @return 存储 key
     */
    String upload(String key, InputStream inputStream, long contentLength, String contentType);

    /**
     * 下载文件
     */
    InputStream download(String key);

    /**
     * 删除文件
     */
    void delete(String key);

    /**
     * 生成预签名 URL（在线预览/下载）
     */
    String generatePresignedUrl(String key, int expirySeconds);
}
