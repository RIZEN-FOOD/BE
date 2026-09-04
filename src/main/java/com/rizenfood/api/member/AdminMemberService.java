package com.rizenfood.api.member;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.rizenfood.api.common.NotFoundException;
import com.rizenfood.api.member.dto.AdminMemberDtos;
import com.rizenfood.api.order.Order;
import com.rizenfood.api.order.OrderRepository;

/**
 * 관리자 회원 관리.
 *
 * ★ 개인정보 최소 취급 (CLAUDE.md 규칙 6 · 개인정보).
 *   - 목록·상세는 휴대폰을 마스킹(010-****-5678)해서만 준다.
 *   - 전체 번호는 revealPhone() 로만 나가며, 호출은 컨트롤러가 감사로그에 남긴다.
 *   - 비밀번호 해시는 어떤 응답에도 담지 않는다.
 *
 * ★ 조치는 잠금 해제 / 정지·해제 두 가지. 탈퇴한 회원에는 상태를 바꾸지 않는다.
 *   실제 삭제(파기)는 여기서 하지 않는다 — 회원 탈퇴 플로우(withdraw)와 purge 스케줄의 몫이다.
 */
@Service
public class AdminMemberService {

    private final MemberRepository memberRepo;
    private final OrderRepository orderRepo;
    private final PhoneCipher phoneCipher;

    public AdminMemberService(MemberRepository memberRepo,
                              OrderRepository orderRepo,
                              PhoneCipher phoneCipher) {
        this.memberRepo = memberRepo;
        this.orderRepo = orderRepo;
        this.phoneCipher = phoneCipher;
    }

    @Transactional(readOnly = true)
    public Page<AdminMemberDtos.ListItem> list(String q, String status, Pageable pageable) {
        String normalizedQ = (q == null) ? "" : q.trim();
        String normalizedStatus = (status == null) ? "" : status.trim();
        return memberRepo.searchForAdmin(normalizedQ, normalizedStatus, pageable)
                .map(this::toListItem);
    }

    @Transactional(readOnly = true)
    public AdminMemberDtos.Detail detail(Long id) {
        Member m = memberRepo.findById(id)
                .orElseThrow(() -> new NotFoundException("회원을 찾을 수 없습니다."));

        long orderCount = orderRepo.countByMemberId(id);
        AdminMemberDtos.RecentOrder recent = orderRepo
                .findByMemberIdOrderByOrderedAtDesc(id, PageRequest.of(0, 1))
                .stream().findFirst()
                .map(this::toRecentOrder)
                .orElse(null);

        String phoneMasked = maskPhone(m.getPhoneEncrypted());

        return new AdminMemberDtos.Detail(
                m.getId(), m.getEmail(), m.getName(), m.getProvider(), m.getStatus(),
                m.isLocked(), m.getLockedUntil(), m.getFailedCount(),
                phoneMasked, phoneMasked != null,
                m.getTermsAgreedAt(), m.getPrivacyAgreedAt(), m.getMarketingAgreedAt(),
                m.getAgeVerifiedAt(), m.getLastLoginAt(), m.getCreatedAt(), m.getWithdrawnAt(),
                new AdminMemberDtos.OrderSummary(orderCount, recent));
    }

    /** 전체 휴대폰 번호. 호출 자체를 컨트롤러가 감사로그에 남긴다. */
    @Transactional(readOnly = true)
    public String revealPhone(Long id) {
        Member m = memberRepo.findById(id)
                .orElseThrow(() -> new NotFoundException("회원을 찾을 수 없습니다."));
        String phone = phoneCipher.decrypt(m.getPhoneEncrypted());
        if (phone == null || phone.isBlank()) {
            throw new NotFoundException("등록된 휴대폰 번호가 없습니다.");
        }
        return phone;
    }

    @Transactional
    public void unlock(Long id) {
        Member m = memberRepo.findById(id)
                .orElseThrow(() -> new NotFoundException("회원을 찾을 수 없습니다."));
        m.adminUnlock();
    }

    /** 상태 변경. target 은 ACTIVE 또는 SUSPENDED 만 허용한다. */
    @Transactional
    public void changeStatus(Long id, String target) {
        Member m = memberRepo.findById(id)
                .orElseThrow(() -> new NotFoundException("회원을 찾을 수 없습니다."));

        if (m.isWithdrawn()) {
            throw new IllegalArgumentException("탈퇴한 회원의 상태는 변경할 수 없습니다.");
        }
        if ("SUSPENDED".equals(target)) {
            m.adminSuspend();
        } else if ("ACTIVE".equals(target)) {
            m.adminReactivate();
        } else {
            throw new IllegalArgumentException("허용되지 않은 상태입니다.");
        }
    }

    // ── 매핑 ──────────────────────────────────────────

    private AdminMemberDtos.ListItem toListItem(Member m) {
        return new AdminMemberDtos.ListItem(
                m.getId(), m.getEmail(), m.getName(), m.getProvider(), m.getStatus(),
                m.isLocked(), maskPhone(m.getPhoneEncrypted()),
                m.getLastLoginAt(), m.getCreatedAt());
    }

    private AdminMemberDtos.RecentOrder toRecentOrder(Order o) {
        return new AdminMemberDtos.RecentOrder(
                o.getOrderNo(), o.getStatus(), o.getTotalAmount(), o.getOrderedAt());
    }

    /**
     * 휴대폰 마스킹. 암호문을 복호화한 뒤 가운데를 가린다.
     * 01012345678 → 010-****-5678. 번호가 없으면 null.
     */
    private String maskPhone(String encrypted) {
        String plain = phoneCipher.decrypt(encrypted);
        if (plain == null || plain.isBlank()) {
            return null;
        }
        String digits = plain.replaceAll("\\D", "");
        if (digits.length() < 4) {
            return "****";
        }
        String last4 = digits.substring(digits.length() - 4);
        String first3 = digits.length() >= 10 ? digits.substring(0, 3) : "";
        return (first3.isEmpty() ? "" : first3 + "-") + "****-" + last4;
    }
}
