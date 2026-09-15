package com.rizenfood.api.image.storage;

import java.net.URI;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.core.checksums.RequestChecksumCalculation;
import software.amazon.awssdk.core.checksums.ResponseChecksumValidation;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;

/**
 * S3 호환 저장소 클라이언트. AWS S3 와 Cloudflare R2 둘 다 이것으로 붙는다.
 *
 * 키는 코드·설정 파일에 두지 않는다. 표준 환경변수 AWS_ACCESS_KEY_ID / AWS_SECRET_ACCESS_KEY 로
 * 넣는다(R2 도 같은 이름에 R2 API 토큰의 키를 넣는다). EC2 처럼 역할(IAM Role)이 붙은 서버면
 * 환경변수 없이도 역할 자격증명을 자동으로 쓴다.
 *
 * R2 로 쓸 때 (app.storage.s3.endpoint 를 채우면)
 *   - endpoint: https://<계정ID>.r2.cloudflarestorage.com
 *   - region: auto
 *   - 경로 방식 주소를 쓰고, R2 가 요구하지 않는 체크섬 헤더는 필요할 때만 붙인다.
 */
@Configuration
@ConditionalOnProperty(name = "app.storage.type", havingValue = "s3")
public class S3StorageConfig {

    @Bean(destroyMethod = "close")
    public S3Client s3Client(
            @Value("${app.storage.s3.region:ap-northeast-2}") String region,
            @Value("${app.storage.s3.endpoint:}") String endpoint) {

        S3ClientBuilder builder = S3Client.builder()
                .region(Region.of(region))
                .credentialsProvider(DefaultCredentialsProvider.create());

        if (endpoint != null && !endpoint.isBlank()) {
            builder.endpointOverride(URI.create(endpoint.trim()))
                    .forcePathStyle(true)
                    .requestChecksumCalculation(RequestChecksumCalculation.WHEN_REQUIRED)
                    .responseChecksumValidation(ResponseChecksumValidation.WHEN_REQUIRED);
        }
        return builder.build();
    }
}
