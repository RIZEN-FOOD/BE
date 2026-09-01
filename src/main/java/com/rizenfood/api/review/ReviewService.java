package com.rizenfood.api.review;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.rizenfood.api.common.NotFoundException;
import com.rizenfood.api.member.Member;
import com.rizenfood.api.member.MemberRepository;
import com.rizenfood.api.product.Product;
import com.rizenfood.api.product.ProductRepository;
import com.rizenfood.api.review.dto.ReviewDtos;

/**
 * 후기 읽기·쓰기.
 *
 * ★ 작성은 로그인 필요 (기획서 §4.2). 관리자가 승인해야 공개된다(visible 기본 false).
 *   구매 인증(verified_purchase)은 주문 기능이 생기기 전까지 항상 false 다 —
 *   실제로 산 사람인지 확인할 방법이 아직 없는데 배지를 붙이면 그 자체가 거짓 표시다.
 */
@Service
public class ReviewService {

    private static final int MAX_IMAGES = 5;

    private final ReviewRepository repository;
    private final ProductRepository productRepository;
    private final MemberRepository memberRepository;
    private final ReviewMapper mapper;

    public ReviewService(ReviewRepository repository, ProductRepository productRepository,
                         MemberRepository memberRepository, ReviewMapper mapper) {
        this.repository = repository;
        this.productRepository = productRepository;
        this.memberRepository = memberRepository;
        this.mapper = mapper;
    }

    // ── 공개 ─────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Page<ReviewDtos.Item> listPublic(Long productId, Pageable pageable) {
        Page<Review> page = productId == null
                ? repository.findByVisibleTrue(pageable)
                : repository.findByProductIdAndVisibleTrue(productId, pageable);
        return page.map(mapper::toItem);
    }

    @Transactional(readOnly = true)
    public ReviewDtos.Stats stats(Long productId) {
        double avg = repository.averageRating(productId).orElse(0.0);
        long count = repository.countByProductIdAndVisibleTrue(productId);
        return new ReviewDtos.Stats(Math.round(avg * 10) / 10.0, count);
    }

    // ── 회원 ─────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Page<ReviewDtos.Item> listMine(Long memberId, Pageable pageable) {
        return repository.findByMemberId(memberId, pageable).map(mapper::toItem);
    }

    @Transactional
    public Long create(Long memberId, ReviewDtos.CreateRequest request) {
        Product product = productRepository.findById(request.productId())
                .filter(Product::isVisible)
                .orElseThrow(() -> new NotFoundException("상품을 찾을 수 없습니다."));
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new NotFoundException("회원을 찾을 수 없습니다."));

        Review review = new Review(product, memberId, request.rating().shortValue(),
                request.content(), member.getName());

        List<String> images = request.imageKeys() == null ? List.of() : request.imageKeys();
        if (images.size() > MAX_IMAGES) {
            throw new IllegalArgumentException("사진은 최대 " + MAX_IMAGES + "장까지 첨부할 수 있습니다.");
        }
        review.replaceImages(images);

        return repository.save(review).getId();
    }

    /** 본인 후기만 지울 수 있다. */
    @Transactional
    public void deleteMine(Long memberId, Long reviewId) {
        if (!repository.existsByIdAndMemberId(reviewId, memberId)) {
            throw new NotFoundException("후기를 찾을 수 없습니다.");
        }
        repository.deleteById(reviewId);
    }

    // ── 관리자 ───────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Page<ReviewDtos.AdminItem> listForAdmin(Boolean pendingOnly, Pageable pageable) {
        Page<Review> page = Boolean.TRUE.equals(pendingOnly)
                ? repository.findByVisibleFalse(pageable)
                : repository.findAll(pageable);
        return page.map(mapper::toAdminItem);
    }

    @Transactional
    public void moderate(Long reviewId, boolean visible, String reason) {
        Review review = repository.findById(reviewId)
                .orElseThrow(() -> new NotFoundException("후기를 찾을 수 없습니다."));
        if (visible) {
            review.approve();
        } else {
            review.hide(reason);
        }
    }

    @Transactional
    public void deleteAsAdmin(Long reviewId) {
        if (!repository.existsById(reviewId)) {
            throw new NotFoundException("후기를 찾을 수 없습니다.");
        }
        repository.deleteById(reviewId);
    }
}
