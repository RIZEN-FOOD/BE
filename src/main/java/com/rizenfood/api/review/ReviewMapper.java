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
                r.getId(), r.getAuthorName(), r.getRating(), r.getContent(),
                r.isVerifiedPurchase(), r.isSponsored(),
                r.getImages().stream().map(this::imageUrl).toList(),
                r.getCreatedAt(),
                r.getProduct().getSlug(), r.getProduct().getNameKo(), thumbnailUrl(r));
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
