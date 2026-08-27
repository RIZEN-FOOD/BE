package com.rizenfood.api.image;

import java.util.Map;

/**
 * 파이프라인을 통과한 이미지 한 장의 결과.
 *
 * @param key       저장소 상의 기본 키. 실제 파일은 이 키 아래 크기별로 놓인다.
 * @param variants  크기별 저장 키
 * @param width     재인코딩 직후 원본 픽셀 크기
 * @param height    재인코딩 직후 원본 픽셀 크기
 */
public record ProcessedImage(
        String key,
        Map<ImageVariant, String> variants,
        int width,
        int height) {
}
