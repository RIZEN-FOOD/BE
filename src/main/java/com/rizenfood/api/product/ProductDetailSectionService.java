package com.rizenfood.api.product;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.rizenfood.api.common.NotFoundException;
import com.rizenfood.api.image.ImageService;
import com.rizenfood.api.image.ImageVariant;
import com.rizenfood.api.product.dto.ProductDtos;

/**
 * 상품 상세 페이지 섹션.
 *
 * 관리자 화면이 순서대로 정리한 목록을 통째로 보내면 그대로 저장한다(전체 교체).
 * 블록 하나하나에 id 를 관리하면 화면이 복잡해지고, 순서 바꾸기·삭제가 잦은 화면이라 이 편이 단순하다.
 *
 * ★ 저장 전에 종류별 필수값을 검사한다. 이미지 섹션에 사진이 없거나 영상 섹션에 주소·파일이
 *   둘 다 없으면 화면에 빈칸이 생기므로 거절한다.
 */
@Service
public class ProductDetailSectionService {

    /** 유튜브 주소만 받는다. 아무 주소나 iframe 으로 띄우면 위험하다. */
    private static final String YOUTUBE_PATTERN =
            "^https://(www\\.youtube\\.com/(watch\\?v=|embed/|shorts/)|youtu\\.be/)[A-Za-z0-9_-]{6,20}([&?][^\\s]*)?$";

    private final ProductDetailSectionRepository repository;
    private final ProductRepository productRepository;
    private final ImageService imageService;

    public ProductDetailSectionService(ProductDetailSectionRepository repository,
                                       ProductRepository productRepository,
                                       ImageService imageService) {
        this.repository = repository;
        this.productRepository = productRepository;
        this.imageService = imageService;
    }

    @Transactional(readOnly = true)
    public List<ProductDtos.DetailSectionItem> listPublic(Long productId) {
        return repository.findByProductIdOrderBySortOrderAscIdAsc(productId).stream()
                .filter(ProductDetailSection::isVisible)
                .filter(this::hasContent)
                .map(this::toItem)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ProductDtos.AdminDetailSection> listForAdmin(Long productId) {
        return repository.findByProductIdOrderBySortOrderAscIdAsc(productId).stream()
                .map(this::toAdminItem)
                .toList();
    }

    /** 관리자 화면이 보낸 순서대로 통째로 저장한다. */
    @Transactional
    public void replaceAll(Long productId, List<ProductDtos.DetailSectionRequest> requests) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new NotFoundException("상품을 찾을 수 없습니다."));

        repository.deleteByProductId(productId);
        repository.flush();

        if (requests == null || requests.isEmpty()) {
            return;
        }
        int order = 1;
        for (ProductDtos.DetailSectionRequest r : requests) {
            validate(r);
            ProductDetailSection s = new ProductDetailSection(product, r.type());
            s.setSortOrder(order++);
            s.setVisible(r.visible());
            s.setImageKey(r.imageKey());
            s.setAltText(r.altText());
            s.setVideoUrl(r.videoUrl());
            s.setVideoFileKey(r.videoFileKey());
            s.setThumbnailKey(r.thumbnailKey());
            s.setHeading(r.heading());
            s.setBody(r.body());
            s.setCaption(r.caption());
            s.touch();
            repository.save(s);
        }
    }

    private void validate(ProductDtos.DetailSectionRequest r) {
        switch (r.type()) {
            case "IMAGE" -> {
                if (isBlank(r.imageKey())) {
                    throw new IllegalArgumentException("이미지 블록에 사진을 올려 주세요.");
                }
            }
            case "VIDEO" -> {
                boolean hasUrl = !isBlank(r.videoUrl());
                boolean hasFile = !isBlank(r.videoFileKey());
                if (!hasUrl && !hasFile) {
                    throw new IllegalArgumentException("영상 블록에 유튜브 주소를 넣거나 영상 파일을 올려 주세요.");
                }
                if (hasUrl && !r.videoUrl().trim().matches(YOUTUBE_PATTERN)) {
                    throw new IllegalArgumentException("영상 주소는 유튜브 주소만 넣을 수 있습니다.");
                }
            }
            case "TEXT" -> {
                if (isBlank(r.heading()) && isBlank(r.body())) {
                    throw new IllegalArgumentException("글 블록에 제목이나 내용을 입력해 주세요.");
                }
            }
            default -> throw new IllegalArgumentException("블록 종류가 올바르지 않습니다.");
        }
    }

    /** 내용이 비어 있는 블록은 공개 화면에 내보내지 않는다. */
    private boolean hasContent(ProductDetailSection s) {
        return switch (s.getType()) {
            case "IMAGE" -> s.getImageKey() != null;
            case "VIDEO" -> s.getVideoUrl() != null || s.getVideoFileKey() != null;
            case "TEXT" -> s.getHeading() != null || s.getBody() != null;
            default -> false;
        };
    }

    private ProductDtos.DetailSectionItem toItem(ProductDetailSection s) {
        return new ProductDtos.DetailSectionItem(
                s.getType(),
                url(s.getImageKey(), ImageVariant.LARGE),
                s.getAltText(),
                s.getVideoUrl(),
                fileUrl(s.getVideoFileKey()),
                url(s.getThumbnailKey(), ImageVariant.LARGE),
                s.getHeading(),
                s.getBody(),
                s.getCaption());
    }

    private ProductDtos.AdminDetailSection toAdminItem(ProductDetailSection s) {
        return new ProductDtos.AdminDetailSection(
                s.getType(), s.isVisible(),
                s.getImageKey(), url(s.getImageKey(), ImageVariant.MEDIUM),
                s.getAltText(),
                s.getVideoUrl(),
                s.getVideoFileKey(), fileUrl(s.getVideoFileKey()),
                s.getThumbnailKey(), url(s.getThumbnailKey(), ImageVariant.MEDIUM),
                s.getHeading(), s.getBody(), s.getCaption());
    }

    private String url(String baseKey, ImageVariant variant) {
        if (isBlank(baseKey)) {
            return null;
        }
        return imageService.urlOf("%s_%s.webp".formatted(baseKey, variant.suffix()));
    }

    /** 영상 파일은 변환하지 않고 올린 그대로 서빙한다. 키에 확장자가 들어 있다. */
    private String fileUrl(String key) {
        return isBlank(key) ? null : imageService.urlOf(key);
    }

    private static boolean isBlank(String v) {
        return v == null || v.isBlank();
    }
}
