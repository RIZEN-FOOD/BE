package com.rizenfood.api.image.storage;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

/**
 * 운영 저장소. S3 에 올리고 CloudFront 로 서빙한다.
 *
 * 버킷은 퍼블릭 읽기를 열지 않는다. CloudFront(OAC)만 읽게 두고
 * 공개 URL 은 CloudFront 도메인을 쓴다. 그래야 버킷 주소가 노출되지 않고
 * 캐시·압축·HTTPS 를 CloudFront 가 맡는다.
 */
@Component
@ConditionalOnProperty(name = "app.storage.type", havingValue = "s3")
public class S3ImageStorage implements ImageStorage {

    private final S3Client client;
    private final String bucket;
    private final String publicBaseUrl;

    public S3ImageStorage(
            S3Client client,
            @Value("${app.storage.s3.bucket}") String bucket,
            @Value("${app.storage.s3.public-base-url}") String publicBaseUrl) {
        this.client = client;
        this.bucket = bucket;
        this.publicBaseUrl = publicBaseUrl.endsWith("/")
                ? publicBaseUrl.substring(0, publicBaseUrl.length() - 1)
                : publicBaseUrl;
    }

    @Override
    public String put(String key, byte[] bytes, String contentType) {
        client.putObject(
                PutObjectRequest.builder()
                        .bucket(bucket)
                        .key(key)
                        .contentType(contentType)
                        // 이미지 키에 내용 해시가 들어가므로 오래 캐시해도 안전하다.
                        .cacheControl("public, max-age=31536000, immutable")
                        // 브라우저가 Content-Type 을 추측하지 못하게 한다.
                        .contentDisposition("inline")
                        .build(),
                RequestBody.fromBytes(bytes));
        return urlOf(key);
    }

    @Override
    public void delete(String key) {
        client.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(key).build());
    }

    @Override
    public String urlOf(String key) {
        return publicBaseUrl + "/" + key;
    }
}
