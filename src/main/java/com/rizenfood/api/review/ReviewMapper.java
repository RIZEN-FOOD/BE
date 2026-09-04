package com.rizenfood.api.review;

import org.springframework.stereotype.Component;

import com.rizenfood.api.image.ImageService;
import com.rizenfood.api.image.ImageVariant;
import com.rizenfood.api.review.dto.ReviewDtos;

@Component
public class ReviewMapper {

    private final ImageService imageService;

    public ReviewMapper(ImageService imageService) {
        this.imageService = imageService;
    }

    public ReviewDtos.Item toItem(Review r) {
        return new ReviewDtos.Item(
                r.getId(), maskName(r.getAuthorName()), r.getRating(), r.getContent(),
                r.isVerifiedPurchase(), r.isSponsored(),
                r.getImages().stream().map(this::imageUrl).toList(),
                r.getCreatedAt(),
                r.getProduct().getSlug(), r.getProduct().getNameKo(), thumbnailUrl(r));
    }

    /**
     * 공개 후기의 작성자 이름을 가린다 — 앞 글자만 남기고 나머지는 * 로 처리.
     * 개인정보 노출을 줄인다 (예: 김도현 → 김**, 이수 → 이*).
     * 관리자용(toAdminItem)에는 적용하지 않는다.
     */
    private String maskName(String name) {
        if (name == null || name.isBlank()) {
            return "익명";
        }
        String trimmed = name.trim();
        if (trimmed.length() == 1) {
            return trimmed;
        }
        return trimmed.charAt(0) + "*".repeat(trimmed.length() - 1);
    }

    public ReviewDtos.AdminItem toAdminItem(Review r) {
        return new ReviewDtos.AdminItem(
                r.getId(), r.getAuthorName(), r.getMemberId(), r.getRating(), r.getContent(),
                r.isVisible(), r.isSponsored(), r.getHiddenReason(),
                r.getImages().stream().map(this::imageUrl).toList(),
                r.getCreatedAt(),
                r.getProduct().getSlug(), r.getProduct().getNameKo());
    }

    private String imageUrl(ReviewImage img) {
        return imageService.urlOf("%s_%s.webp".formatted(img.getImageKey(), ImageVariant.MEDIUM.suffix()));
    }

    private String thumbnailUrl(Review r) {
        String key = r.getProduct().getThumbnailKey();
        if (key == null || key.isBlank()) {
            return null;
        }
        return imageService.urlOf("%s_%s.webp".formatted(key, ImageVariant.THUMBNAIL.suffix()));
    }
}
