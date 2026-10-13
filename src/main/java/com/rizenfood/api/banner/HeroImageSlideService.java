package com.rizenfood.api.banner;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.rizenfood.api.banner.dto.HeroImageSlideDtos;
import com.rizenfood.api.common.NotFoundException;
import com.rizenfood.api.image.ImageService;
import com.rizenfood.api.image.ImageVariant;

/**
 * 메인 히어로 — 이미지 한 장 슬라이드 (V41, 2026-10-13).
 *
 * 화면 전체를 덮으므로 PC·휴대폰 모두 큰 사진(large, 가로 최대 2000px)을 쓴다.
 */
@Service
public class HeroImageSlideService {

    private final HeroImageSlideRepository repository;
    private final ImageService imageService;

    public HeroImageSlideService(HeroImageSlideRepository repository, ImageService imageService) {
        this.repository = repository;
        this.imageService = imageService;
    }

    // ── 공개 ─────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<HeroImageSlideDtos.PublicItem> listPublic() {
        return repository.findByEnabledTrueOrderBySortOrderAscIdAsc().stream()
                .map(this::toPublic)
                .toList();
    }

    // ── 관리자 ───────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<HeroImageSlideDtos.AdminItem> listForAdmin() {
        return repository.findAllByOrderBySortOrderAscIdAsc().stream().map(this::toAdmin).toList();
    }

    @Transactional(readOnly = true)
    public HeroImageSlideDtos.AdminItem get(Long id) {
        return toAdmin(load(id));
    }

    @Transactional
    public Long create(HeroImageSlideDtos.SaveRequest r) {
        HeroImageSlide s = new HeroImageSlide(r.imagePcKey(), r.altText().trim());
        apply(s, r);
        return repository.save(s).getId();
    }

    @Transactional
    public void update(Long id, HeroImageSlideDtos.SaveRequest r) {
        HeroImageSlide s = load(id);
        s.setImagePcKey(r.imagePcKey());
        s.setAltText(r.altText().trim());
        apply(s, r);
    }

    @Transactional
    public void delete(Long id) {
        repository.delete(load(id));
    }

    @Transactional
    public void setEnabled(Long id, boolean enabled) {
        HeroImageSlide s = load(id);
        s.setEnabled(enabled);
        s.touch();
    }

    private HeroImageSlide load(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new NotFoundException("배너를 찾을 수 없습니다."));
    }

    private void apply(HeroImageSlide s, HeroImageSlideDtos.SaveRequest r) {
        s.setImageMobileKey(blankToNull(r.imageMobileKey()));
        s.setButtonLabel(blankToNull(r.buttonLabel()));
        s.setLinkUrl(blankToNull(r.linkUrl()));
        s.setOpenNewTab(r.openNewTab());
        s.setSortOrder(r.sortOrder());
        s.setEnabled(r.enabled());
        s.touch();
    }

    private HeroImageSlideDtos.PublicItem toPublic(HeroImageSlide s) {
        boolean button = s.getButtonLabel() != null && s.getLinkUrl() != null;
        return new HeroImageSlideDtos.PublicItem(
                s.getId(),
                url(s.getImagePcKey()),
                url(s.getImageMobileKey()),
                s.getAltText(),
                button ? s.getButtonLabel() : null,
                button ? s.getLinkUrl() : null,
                s.isOpenNewTab(),
                s.getSortOrder());
    }

    private HeroImageSlideDtos.AdminItem toAdmin(HeroImageSlide s) {
        return new HeroImageSlideDtos.AdminItem(
                s.getId(),
                s.getImagePcKey(), url(s.getImagePcKey()),
                s.getImageMobileKey(), url(s.getImageMobileKey()),
                s.getAltText(), s.getButtonLabel(), s.getLinkUrl(),
                s.isOpenNewTab(), s.getSortOrder(), s.isEnabled());
    }

    private String url(String baseKey) {
        if (baseKey == null || baseKey.isBlank()) {
            return null;
        }
        return imageService.urlOf("%s_%s.webp".formatted(baseKey, ImageVariant.LARGE.suffix()));
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
