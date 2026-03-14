package com.huah.ai.platform.rag.service;

import com.huah.ai.platform.common.exception.BizException;
import com.huah.ai.platform.rag.config.S3Properties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

import java.io.InputStream;
import java.time.Duration;

@Slf4j
@Service
@RequiredArgsConstructor
public class S3FileStorageService implements FileStorageService {

    private final S3Client s3Client;
    private final S3Presigner s3Presigner;
    private final S3Properties s3Properties;

    @Override
    public String upload(String key, InputStream inputStream, long contentLength, String contentType) {
        try {
            PutObjectRequest request = PutObjectRequest.builder()
                    .bucket(s3Properties.getBucket())
                    .key(key)
                    .contentType(contentType)
                    .build();
            s3Client.putObject(request, RequestBody.fromInputStream(inputStream, contentLength));
            log.info("文件上传S3成功: bucket={}, key={}", s3Properties.getBucket(), key);
            return key;
        } catch (S3Exception e) {
            log.error("S3上传失败: key={}, error={}", key, e.getMessage());
            throw new BizException("文件存储失败: " + e.getMessage());
        }
    }

    @Override
    public InputStream download(String key) {
        try {
            GetObjectRequest request = GetObjectRequest.builder()
                    .bucket(s3Properties.getBucket())
                    .key(key)
                    .build();
            return s3Client.getObject(request);
        } catch (NoSuchKeyException e) {
            throw new BizException("文件不存在: " + key);
        } catch (S3Exception e) {
            log.error("S3下载失败: key={}, error={}", key, e.getMessage());
            throw new BizException("文件下载失败: " + e.getMessage());
        }
    }

    @Override
    public void delete(String key) {
        try {
            DeleteObjectRequest request = DeleteObjectRequest.builder()
                    .bucket(s3Properties.getBucket())
                    .key(key)
                    .build();
            s3Client.deleteObject(request);
            log.info("S3文件删除成功: key={}", key);
        } catch (S3Exception e) {
            log.error("S3删除失败(非致命): key={}, error={}", key, e.getMessage());
        }
    }

    @Override
    public String generatePresignedUrl(String key, int expirySeconds) {
        try {
            GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                    .signatureDuration(Duration.ofSeconds(expirySeconds))
                    .getObjectRequest(b -> b.bucket(s3Properties.getBucket()).key(key))
                    .build();
            return s3Presigner.presignGetObject(presignRequest).url().toString();
        } catch (S3Exception e) {
            log.error("生成预签名URL失败: key={}, error={}", key, e.getMessage());
            throw new BizException("生成预览链接失败: " + e.getMessage());
        }
    }
}
