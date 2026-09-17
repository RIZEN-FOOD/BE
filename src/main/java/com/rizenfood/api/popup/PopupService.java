package com.rizenfood.api.popup;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.rizenfood.api.common.NotFoundException;
import com.rizenfood.api.image.ImageService;
import com.rizenfood.api.image.ImageVariant;

/**
 * 팝업 읽기·쓰기.
 *
 * 저장 키 대신 공개 URL 을 내보낸다. 팝업은 휴대폰 폭 정도로 뜨므로 중간 크기(medium)를 쓴다.
 */
@Service
public class PopupService {

    private final PopupRepository repository;
    private final ImageService imageService;

    public PopupService(PopupRepository repository, ImageService imageService) {
        this.repository = repository;
        this.imageService = imageService;
    }

    // ── 공개 ─────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<PopupDtos.PublicItem> listActive() {
        return repository.findActive(Instant.now()).stream()
                .filter(Popup::isActiveNow)
                .map(p -> new PopupDtos.PublicItem(
                        p.getId(), p.getTitle(),
                        url(p.getImageKey(), ImageVariant.LARGE),
                        p.isShowHideToday(),
                        p.isShowLinkButton() && p.getLinkUrl() != null,
                        p.getLinkUrl(),
                        p.getUpdatedAt() == null ? 0 : p.getUpdatedAt().toEpochMilli()))
                .toList();
    }

    // ── 관리자 ───────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<PopupDtos.AdminItem> listForAdmin() {
        return repository.findAllByOrderBySortOrderAscIdAsc().stream().map(this::toAdmin).toList();
    }

    @Transactional(readOnly = true)
    public PopupDtos.AdminItem getForAdmin(Long id) {
        return toAdmin(find(id));
    }

    @Transactional
    public Long create(PopupDtos.SaveRequest r) {
        validate(r);
        Popup popup = new Popup(r.title().trim(), r.imageKey());
        popup.setSortOrder(repository.findMaxSortOrder() + 1);
        apply(popup, r);
        return repository.save(popup).getId();
    }

    @Transactional
    public void update(Long id, PopupDtos.SaveRequest r) {
        validate(r);
        Popup popup = find(id);
        popup.setTitle(r.title().trim());
        popup.setImageKey(r.imageKey());
        apply(popup, r);
    }

    @Transactional
    public void delete(Long id) {
        repository.delete(find(id));
    }

    @Transactional
    public void updateVisibility(Long id, boolean visible) {
        Popup popup = find(id);
        popup.setVisible(visible);
        popup.touch();
    }

    /** 목록 순서 저장. 목록에 없는 팝업이 섞이면 거절한다. */
    @Transactional
    public void reorder(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        Map<Long, Popup> all = repository.findAll().stream()
                .collect(Collectors.toMap(Popup::getId, Function.identity()));
        if (ids.size() != all.size() || !all.keySet().containsAll(ids)) {
            throw new IllegalArgumentException("목록이 바뀌었습니다. 새로고침 후 다시 시도해 주세요.");
        }
        for (int i = 0; i < ids.size(); i++) {
            Popup p = all.get(ids.get(i));
            p.setSortOrder(i + 1);
        }
    }

    private Popup find(Long id) {
        return repository.findById(id).orElseThrow(() -> new NotFoundException("팝업을 찾을 수 없습니다."));
    }

    private void validate(PopupDtos.SaveRequest r) {
        if (r.showLinkButton() && (r.linkUrl() == null || r.linkUrl().isBlank())) {
            throw new IllegalArgumentException("이동 버튼을 보이려면 이동할 주소를 입력해 주세요.");
        }
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

    private void apply(Popup p, PopupDtos.SaveRequest r) {
        p.setLinkUrl(r.linkUrl() == null || r.linkUrl().isBlank() ? null : r.linkUrl().trim());
        p.setShowHideToday(r.showHideToday());
        p.setShowLinkButton(r.showLinkButton());
        p.setAlwaysOn(r.alwaysOn());
        p.setStartAt(r.alwaysOn() ? null : r.startAt());
        p.setEndAt(r.alwaysOn() ? null : r.endAt());
        p.setVisible(r.visible());
        p.touch();
    }

    private PopupDtos.AdminItem toAdmin(Popup p) {
        return new PopupDtos.AdminItem(
                p.getId(), p.getTitle(), url(p.getImageKey(), ImageVariant.MEDIUM), p.getImageKey(),
                p.isShowHideToday(), p.isShowLinkButton(), p.getLinkUrl(),
                p.isAlwaysOn(), p.getStartAt(), p.getEndAt(),
                p.getSortOrder(), p.isVisible(), p.isActiveNow());
    }

    private String url(String baseKey, ImageVariant variant) {
        if (baseKey == null || baseKey.isBlank()) {
            return null;
        }
        return imageService.urlOf("%s_%s.webp".formatted(baseKey, variant.suffix()));
    }
}
