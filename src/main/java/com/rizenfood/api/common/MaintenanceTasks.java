package com.rizenfood.api.common;

import java.time.Duration;
import java.time.Instant;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.rizenfood.api.cart.CartRepository;
import com.rizenfood.api.member.MemberRepository;
import com.rizenfood.api.member.RefreshTokenRepository;

/**
 * 정리 작업 본체.
 *
 * ★ MaintenanceScheduler 와 따로 둔다. 같은 클래스 안에서 자기 메서드를 부르면
 *   스프링 프록시를 타지 않아 @Transactional 이 걸리지 않고, 삭제 쿼리는 트랜잭션이 없으면
 *   전부 실패한다. 그러면 "매일 정리하는 것처럼 보이지만 실제로는 아무것도 안 지워지는"
 *   상태가 된다 — 실제로 그렇게 만들었다가 잡은 문제다.
 */
@Component
public class MaintenanceTasks {

    /** 이 기간 손대지 않은 비회원 장바구니는 지운다. */
    static final Duration GUEST_CART_KEEP = Duration.ofDays(30);

    private final CartRepository cartRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final MemberRepository memberRepository;

    public MaintenanceTasks(CartRepository cartRepository,
                            RefreshTokenRepository refreshTokenRepository,
                            MemberRepository memberRepository) {
        this.cartRepository = cartRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.memberRepository = memberRepository;
    }

    /** 쿠키 없이 장바구니를 부를 때마다 생기는 빈 장바구니를 치운다. */
    @Transactional
    public int deleteStaleGuestCarts() {
        return cartRepository.deleteStaleGuestCarts(Instant.now().minus(GUEST_CART_KEEP));
    }

    /** 만료된 로그인 토큰. 회원 id·접속 기기·IP 가 남아 있어 오래 둘 이유가 없다. */
    @Transactional
    public void deleteExpiredRefreshTokens() {
        refreshTokenRepository.deleteExpired(Instant.now());
    }

    /** 탈퇴 후 보존기간이 지난 회원 파기 (개인정보 보호법). */
    @Transactional
    public int purgeWithdrawnMembers() {
        return memberRepository.purgeWithdrawn(Instant.now());
    }
}
