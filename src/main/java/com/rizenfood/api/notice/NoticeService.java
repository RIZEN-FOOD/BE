package com.rizenfood.api.notice;

import java.time.Instant;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.rizenfood.api.common.HtmlSanitizer;
import com.rizenfood.api.common.NotFoundException;
import com.rizenfood.api.notice.dto.NoticeDtos;

/**
 * 공지사항 읽기·쓰기.
 *
 * 공개 조회는 발행 시각이 지난 것만 본다. 예약 발행·임시저장은 공개에 안 나간다.
 * 본문은 저장 시 HtmlSanitizer 를 거친다 — 저장형 XSS 의 주요 경로다.
 */
@Service
public class NoticeService {

    private final NoticeRepository repository;
    private final HtmlSanitizer sanitizer;

    public NoticeService(NoticeRepository repository, HtmlSanitizer sanitizer) {
        this.repository = repository;
        this.sanitizer = sanitizer;
    }

    // ── 공개 ─────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Page<NoticeDtos.PublicListItem> listPublic(String keyword, Pageable pageable) {
        Instant now = Instant.now();
        Page<Notice> page = (keyword == null || keyword.isBlank())
                ? repository.findPublic(now, pageable)
                : repository.searchPublic(now, keyword.trim(), pageable);
        return page.map(this::toPublicListItem);
    }

    /**
     * 공개 상세. 조회수를 올린다.
     * 조회수 증가는 별도 UPDATE 라 본문 읽기와 트랜잭션이 얽히지 않는다.
     */
    @Transactional
    public NoticeDtos.PublicDetail getPublic(Long id) {
        Notice notice = repository.findPublicById(id, Instant.now())
                .orElseThrow(() -> new NotFoundException("공지사항을 찾을 수 없습니다."));
        repository.increaseViewCount(id);
        // 방금 올린 값이 응답에도 반영되도록 +1 해서 준다.
        return new NoticeDtos.PublicDetail(
                notice.getId(), notice.getCategory(), notice.getTitle(), notice.getBodyHtml(),
                notice.getViewCount() + 1, notice.getPublishedAt());
    }

    // ── 관리자 ───────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Page<NoticeDtos.AdminItem> listForAdmin(Pageable pageable) {
        return repository.findAll(pageable).map(this::toAdminItem);
    }

    @Transactional(readOnly = true)
    public NoticeDtos.AdminItem getForAdmin(Long id) {
        return repository.findById(id).map(this::toAdminItem)
                .orElseThrow(() -> new NotFoundException("공지사항을 찾을 수 없습니다."));
    }

    @Transactional
    public Long create(NoticeDtos.SaveRequest r) {
        Notice notice = new Notice(
                r.category() == null ? "NOTICE" : r.category(),
                r.title(),
                sanitizer.clean(r.bodyHtml()));
        notice.setPinned(r.pinned());
        notice.setPublishedAt(r.publishedAt());
        notice.setVisible(r.visible());
        return repository.save(notice).getId();
    }

    @Transactional
    public void update(Long id, NoticeDtos.SaveRequest r) {
        Notice notice = repository.findById(id)
                .orElseThrow(() -> new NotFoundException("공지사항을 찾을 수 없습니다."));
        notice.setCategory(r.category() == null ? "NOTICE" : r.category());
        notice.setTitle(r.title());
        notice.setBodyHtml(sanitizer.clean(r.bodyHtml()));
        notice.setPinned(r.pinned());
        notice.setPublishedAt(r.publishedAt());
        notice.setVisible(r.visible());
        notice.touch();
    }

    @Transactional
    public void delete(Long id) {
        Notice notice = repository.findById(id)
                .orElseThrow(() -> new NotFoundException("공지사항을 찾을 수 없습니다."));
        repository.delete(notice);
    }

    @Transactional
    public void updateVisibility(Long id, boolean visible) {
        Notice notice = repository.findById(id)
                .orElseThrow(() -> new NotFoundException("공지사항을 찾을 수 없습니다."));
        notice.setVisible(visible);
        notice.touch();
    }

    private NoticeDtos.PublicListItem toPublicListItem(Notice n) {
        return new NoticeDtos.PublicListItem(
                n.getId(), n.getCategory(), n.getTitle(), n.isPinned(), n.getViewCount(), n.getPublishedAt());
    }

    private NoticeDtos.AdminItem toAdminItem(Notice n) {
        return new NoticeDtos.AdminItem(
                n.getId(), n.getCategory(), n.getTitle(), n.getBodyHtml(),
                n.isPinned(), n.getViewCount(), n.getPublishedAt(),
                n.isVisible(), n.isPublicNow(), n.getCreatedAt());
    }
}
