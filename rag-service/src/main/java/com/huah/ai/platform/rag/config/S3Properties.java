package com.huah.ai.platform.rag.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "storage.s3")
public class S3Properties {
    private String endpoint;
    private String region;
    private String bucket;
    private String accessKey;
    private String secretKey;
    private int presignedUrlExpiry = 3600;
}
