package com.rizenfood.api.config;

import java.nio.file.Path;
import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.CacheControl;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 로컬에 저장한 업로드 이미지를 /uploads/** 로 서빙한다.
 *
 * 로컬 스토리지(LocalImageStorage)는 파일을 디스크에 쓰기만 하고, 실제 HTTP 서빙은
 * 여기서 정적 리소스 핸들러로 연결한다. 운영에서는 S3+CloudFront 를 쓰면
 * 이 핸들러 대신 CDN 이 서빙하므로 이 설정은 로컬 개발용이다.
 *
 * 공개 읽기는 SecurityConfig 에서 /uploads/** 를 permitAll 로 열어준다.
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final String uploadPath;

    public WebConfig(@Value("${app.storage.local.path:./uploads}") String uploadPath) {
        this.uploadPath = uploadPath;
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String location = Path.of(uploadPath).toAbsolutePath().normalize().toUri().toString();
        // 업로드 파일 이름은 올릴 때마다 새로 만든다(UUID) — 같은 주소의 내용이 바뀌지 않는다.
        // 그래서 1년 동안 다시 묻지 않고 브라우저에 둔다. 재방문 때 사진이 바로 뜬다 (2026-10-13, 전엔 1시간).
        registry.addResourceHandler("/uploads/**")
                .addResourceLocations(location)
                .setCacheControl(CacheControl.maxAge(Duration.ofDays(365)).cachePublic().immutable());
    }
}
