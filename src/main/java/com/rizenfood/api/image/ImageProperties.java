package com.rizenfood.api.image;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 이미지 업로드 설정.
 * 값은 application.yml 의 app.image.* 에 있다.
 */
@ConfigurationProperties(prefix = "app.image")
public record ImageProperties(
        /** 허용 최대 파일 크기(바이트) */
        long maxFileSizeBytes,
        /**
         * 허용 최대 픽셀 수(가로 x 세로).
         * 파일은 작은데 압축을 풀면 수억 픽셀이 되는 파일(압축 폭탄)로
         * 메모리를 고갈시키는 공격을 막는다.
         */
        long maxPixels,
        /** WebP 인코딩 품질 0.0 ~ 1.0 */
        float webpQuality) {

    public ImageProperties {
        if (maxFileSizeBytes <= 0) {
            maxFileSizeBytes = 10L * 1024 * 1024; // 10MB
        }
        if (maxPixels <= 0) {
            maxPixels = 50_000_000L; // 5천만 픽셀
        }
        if (webpQuality <= 0 || webpQuality > 1) {
            webpQuality = 0.82f;
        }
    }
}
