package com.rizenfood.api.image;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.rizenfood.api.image.storage.ImageStorage;

/**
 * 업로드 한 건의 전 과정을 묶는다.
 *
 *   검증 → 재인코딩 → 크기 3종 → WebP → 저장
 *
 * 이 순서를 바꾸지 않는다. 특히 재인코딩 전에 저장하는 경로를 만들면
 * 파이프라인 전체가 무의미해진다.
 */
@Service
public class ImageService {

    private static final DateTimeFormatter PATH_DATE = DateTimeFormatter.ofPattern("yyyy/MM");

    private final ImageValidator validator;
    private final ImageProcessor processor;
    private final ImageStorage storage;

    public ImageService(ImageValidator validator, ImageProcessor processor, ImageStorage storage) {
        this.validator = validator;
        this.processor = processor;
        this.storage = storage;
    }

    /**
     * @param bytes        업로드된 원본 바이트
     * @param originalName 사용자가 올린 파일명. 확장자 확인에만 쓰고 저장에는 쓰지 않는다.
     * @param declaredType 요청의 Content-Type
     * @param category     저장 경로 앞부분 (products, banners, notices, reviews)
     */
    public ProcessedImage upload(byte[] bytes, String originalName, String declaredType, String category) {
        validator.validate(bytes, originalName, declaredType);

        ImageProcessor.Result result = processor.process(bytes);

        // ★ 파일명은 서버가 새로 만든다.
        //   사용자가 준 이름을 쓰면 경로 조작(../), 기존 파일 덮어쓰기,
        //   실행 가능한 확장자 끼워넣기가 전부 가능해진다.
        String base = "%s/%s/%s".formatted(
                safeCategory(category),
                LocalDate.now().format(PATH_DATE),
                UUID.randomUUID().toString().replace("-", ""));

        Map<ImageVariant, String> keys = new EnumMap<>(ImageVariant.class);
        for (Map.Entry<ImageVariant, byte[]> entry : result.variants().entrySet()) {
            String key = "%s_%s.webp".formatted(base, entry.getKey().suffix());
            storage.put(key, entry.getValue(), "image/webp");
            keys.put(entry.getKey(), key);
        }

        return new ProcessedImage(base, keys, result.width(), result.height());
    }

    /** 크기 3종을 함께 지운다. */
    public void delete(ProcessedImage image) {
        image.variants().values().forEach(storage::delete);
    }

    public String urlOf(String key) {
        return storage.urlOf(key);
    }

    /** 경로에 들어갈 수 있는 값만 통과시킨다. */
    private String safeCategory(String category) {
        if (category == null || !category.matches("[a-z][a-z0-9-]{0,29}")) {
            throw new IllegalArgumentException("허용되지 않는 이미지 분류: " + category);
        }
        return category;
    }
}
