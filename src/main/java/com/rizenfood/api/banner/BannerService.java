package com.rizenfood.api.banner;

import java.time.Instant;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.rizenfood.api.banner.dto.BannerDtos;
import com.rizenfood.api.common.NotFoundException;
import com.rizenfood.api.image.ImageService;
import com.rizenfood.api.image.ImageVariant;

/**
 * 배너 읽기·쓰기.
 *
 * 저장 키를 그대로 내보내지 않고 공개 URL 로 바꿔 준다.
 * PC 는 큰 이미지(large), 모바일은 중간(medium)을 쓴다.
 */
@Service
public class BannerService {

    private final BannerRepository repository;
    private final ImageService imageService;

    public BannerService(BannerRepository repository, ImageService imageService) {
        this.repository = repository;
        this.imageService = imageService;
    }

    // ── 공개 ─────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<BannerDtos.PublicItem> listActive(String position) {
        return repository.findActive(position, Instant.now()).stream()
                .filter(Banner::isActiveNow) // SQL 로 걸렀지만 응답 직전 한 번 더 확인
                .map(this::toPublic)
                .toList();
    }

    // ── 관리자 ───────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<BannerDtos.AdminItem> listForAdmin() {
        return repository.findAllByOrderByPositionAscSortOrderAscIdAsc().stream()
                .map(this::toAdmin)
                .toList();
    }

    @Transactional(readOnly = true)
    public BannerDtos.AdminItem getForAdmin(Long id) {
        return repository.findById(id).map(this::toAdmin)
                .orElseThrow(() -> new NotFoundException("배너를 찾을 수 없습니다."));
    }

    @Transactional
    public Long create(BannerDtos.SaveRequest r) {
        validatePeriod(r);
        Banner banner = new Banner(r.title(), r.imagePcKey(), r.imageMobileKey(), r.altText(), r.position());
        banner.setSortOrder(repository.findMaxSortOrder(r.position()) + 1);
        apply(banner, r);
        return repository.save(banner).getId();
    }

    @Transactional
    public void update(Long id, BannerDtos.SaveRequest r) {
        validatePeriod(r);
        Banner banner = repository.findById(id)
                .orElseThrow(() -> new NotFoundException("배너를 찾을 수 없습니다."));
        banner.setTitle(r.title());
        banner.setImagePcKey(r.imagePcKey());
        banner.setImageMobileKey(r.imageMobileKey());
        banner.setAltText(r.altText());
        banner.setPosition(r.position());
        apply(banner, r);
    }

    @Transactional
    public void delete(Long id) {
        Banner banner = repository.findById(id)
                .orElseThrow(() -> new NotFoundException("배너를 찾을 수 없습니다."));
        repository.delete(banner);
    }

    @Transactional
    public void updateVisibility(Long id, boolean visible) {
        Banner banner = repository.findById(id)
                .orElseThrow(() -> new NotFoundException("배너를 찾을 수 없습니다."));
        banner.setVisible(visible);
        banner.touch();
    }

    /** 노출 기간이 상시가 아니면 시작·종료가 있어야 하고 순서가 맞아야 한다. */
    private void validatePeriod(BannerDtos.SaveRequest r) {
        if (r.alwaysOn()) {
            return;
        }
        if (r.startAt() == null || r.endAt() == null) {
            throw new IllegalArgumentException("노출 기간을 정하려면 시작일과 종료일을 모두 입력해 주세요.");
        }
        if (!r.startAt().isBefore(r.endAt())) {
            throw new IllegalArgumentException("종료일이 시작일보다 뒤여야 합니다.");
        }
    }

    private void apply(Banner b, BannerDtos.SaveRequest r) {
        b.setLinkUrl(r.linkUrl() == null || r.linkUrl().isBlank() ? null : r.linkUrl());
        b.setOpenNewTab(r.openNewTab());
        b.setAlwaysOn(r.alwaysOn());
        b.setStartAt(r.alwaysOn() ? null : r.startAt());
        b.setEndAt(r.alwaysOn() ? null : r.endAt());
        b.setVisible(r.visible());
        b.touch();
    }

    private BannerDtos.PublicItem toPublic(Banner b) {
        return new BannerDtos.PublicItem(
                b.getId(),
                url(b.getImagePcKey(), ImageVariant.LARGE),
                url(b.getImageMobileKey(), ImageVariant.MEDIUM),
                b.getAltText(), b.getLinkUrl(), b.isOpenNewTab());
    }

    private BannerDtos.AdminItem toAdmin(Banner b) {
        return new BannerDtos.AdminItem(
                b.getId(), b.getTitle(),
                url(b.getImagePcKey(), ImageVariant.MEDIUM),
                url(b.getImageMobileKey(), ImageVariant.MEDIUM),
                b.getImagePcKey(), b.getImageMobileKey(),
                b.getAltText(), b.getLinkUrl(), b.getPosition(),
                b.isOpenNewTab(), b.isAlwaysOn(), b.getStartAt(), b.getEndAt(),
                b.getSortOrder(), b.isVisible(), b.isActiveNow());
    }

    private String url(String baseKey, ImageVariant variant) {
        if (baseKey == null || baseKey.isBlank()) {
            return null;
        }
        return imageService.urlOf("%s_%s.webp".formatted(baseKey, variant.suffix()));
    }
}
