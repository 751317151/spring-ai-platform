package com.huah.ai.platform.rag.service;

import com.huah.ai.platform.common.exception.BizException;
import com.huah.ai.platform.rag.config.S3Properties;
import com.huah.ai.platform.rag.metrics.RagMetricsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

import java.io.InputStream;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class S3FileStorageService implements FileStorageService {

    private final S3Client s3Client;
    private final S3Presigner s3Presigner;
    private final S3Properties s3Properties;
    private final RagMetricsService metricsService;

    @Override
    public String upload(String key, InputStream inputStream, long contentLength, String contentType) {
        long start = System.nanoTime();
        try {
            PutObjectRequest request = PutObjectRequest.builder()
                    .bucket(s3Properties.getBucket())
                    .key(key)
                    .contentType(contentType)
                    .build();
            s3Client.putObject(request, RequestBody.fromInputStream(inputStream, contentLength));
            metricsService.recordStageLatency("s3.upload", elapsedMillis(start), true);
            log.info("文件上传到 S3 成功: bucket={}, key={}", s3Properties.getBucket(), key);
            return key;
        } catch (S3Exception e) {
            metricsService.recordStageLatency("s3.upload", elapsedMillis(start), false);
            metricsService.recordDependencyFailure("s3", "upload");
            log.error("S3 上传失败: key={}, error={}", key, e.getMessage());
            throw new BizException("文件存储失败: " + e.getMessage());
        }
    }

    @Override
    public InputStream download(String key) {
        long start = System.nanoTime();
        try {
            GetObjectRequest request = GetObjectRequest.builder()
                    .bucket(s3Properties.getBucket())
                    .key(key)
                    .build();
            InputStream objectStream = s3Client.getObject(request);
            metricsService.recordStageLatency("s3.download", elapsedMillis(start), true);
            return objectStream;
        } catch (NoSuchKeyException e) {
            metricsService.recordStageLatency("s3.download", elapsedMillis(start), false);
            metricsService.recordDependencyFailure("s3", "download");
            throw new BizException("文件不存在: " + key);
        } catch (S3Exception e) {
            metricsService.recordStageLatency("s3.download", elapsedMillis(start), false);
            metricsService.recordDependencyFailure("s3", "download");
            log.error("S3 下载失败: key={}, error={}", key, e.getMessage());
            throw new BizException("文件下载失败: " + e.getMessage());
        }
    }

    @Override
    public void delete(String key) {
        long start = System.nanoTime();
        try {
            DeleteObjectRequest request = DeleteObjectRequest.builder()
                    .bucket(s3Properties.getBucket())
                    .key(key)
                    .build();
            s3Client.deleteObject(request);
            metricsService.recordStageLatency("s3.delete", elapsedMillis(start), true);
            log.info("S3 文件删除成功: key={}", key);
        } catch (S3Exception e) {
            metricsService.recordStageLatency("s3.delete", elapsedMillis(start), false);
            metricsService.recordDependencyFailure("s3", "delete");
            log.error("S3 删除失败(非致命): key={}, error={}", key, e.getMessage());
        }
    }

    @Override
    public String generatePresignedUrl(String key, int expirySeconds) {
        long start = System.nanoTime();
        try {
            GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                    .signatureDuration(Duration.ofSeconds(expirySeconds))
                    .getObjectRequest(b -> b.bucket(s3Properties.getBucket()).key(key))
                    .build();
            String url = s3Presigner.presignGetObject(presignRequest).url().toString();
            metricsService.recordStageLatency("s3.presign", elapsedMillis(start), true);
            return url;
        } catch (S3Exception e) {
            metricsService.recordStageLatency("s3.presign", elapsedMillis(start), false);
            metricsService.recordDependencyFailure("s3", "presign");
            log.error("生成预签名 URL 失败: key={}, error={}", key, e.getMessage());
            throw new BizException("生成预览链接失败: " + e.getMessage());
        }
    }

    private long elapsedMillis(long start) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
    }
}
