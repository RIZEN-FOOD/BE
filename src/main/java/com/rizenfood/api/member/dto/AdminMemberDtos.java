package com.rizenfood.api.member.dto;

import java.time.Instant;

/**
 * 관리자 회원 관리 DTO.
 *
 * ★ 개인정보 최소 노출 (CLAUDE.md 규칙 6 · 개인정보).
 *   - 목록·상세 어디에도 비밀번호 해시를 담지 않는다.
 *   - 휴대폰은 기본적으로 마스킹(phoneMasked)해서만 내려준다.
 *     전체 번호는 상세의 별도 '전체 보기' 엔드포인트에서만, 감사로그를 남기고 준다.
 */
public final class AdminMemberDtos {

    private AdminMemberDtos() {
    }

    /** 목록 행. 휴대폰은 마스킹만. */
    public record ListItem(
            Long id,
            String email,
            String name,
            String provider,
            String status,
            boolean locked,
            String phoneMasked,
            Instant lastLoginAt,
            Instant createdAt) {
    }

    /** 상세. 휴대폰은 마스킹만(전체 번호는 별도 엔드포인트). */
    public record Detail(
            Long id,
            String email,
            String name,
            String provider,
            String status,
            boolean locked,
            Instant lockedUntil,
            int failedCount,
            String phoneMasked,
            boolean hasPhone,
            Instant termsAgreedAt,
            Instant privacyAgreedAt,
            Instant marketingAgreedAt,
            Instant ageVerifiedAt,
            Instant lastLoginAt,
            Instant createdAt,
            Instant withdrawnAt,
            OrderSummary orders) {
    }

    /** 회원의 주문 요약. */
    public record OrderSummary(long count, RecentOrder recent) {
    }

    public record RecentOrder(String orderNo, String status, int totalAmount, Instant orderedAt) {
    }

    /** 휴대폰 전체 보기 응답. */
    public record PhoneResponse(String phone) {
    }

    /** 상태 변경 요청. status 는 ACTIVE 또는 SUSPENDED. */
    public record StatusRequest(String status, String reason) {
    }
}
