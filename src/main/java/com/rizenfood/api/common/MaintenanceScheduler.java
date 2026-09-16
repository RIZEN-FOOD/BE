package com.rizenfood.api.common;

import java.time.Duration;
import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.rizenfood.api.cart.CartRepository;
import com.rizenfood.api.member.MemberRepository;
import com.rizenfood.api.member.RefreshTokenRepository;

/**
 * 오래된 데이터 정리. 새벽에 하루 한 번 돈다.
 *
 * 세 가지를 치운다.
 *   1) 손님(비회원) 장바구니 — 쿠키 없이 /api/cart 를 부를 때마다 한 줄씩 생긴다.
 *      치우지 않으면 반복 호출만으로 DB 가 계속 불어난다.
 *   2) 만료된 리프레시 토큰 — 로그인할 때마다 쌓이고, 회원 id·접속 기기·IP 가 남는다.
 *      쓸모가 끝난 기록을 오래 들고 있을 이유가 없다.
 *   3) 탈퇴 후 보존기간이 지난 회원 — 개인정보 보호법상 파기 의무다.
 *      (탈퇴 시점에 이름·이메일·연락처는 이미 지웠다. 여기서 남은 행을 없앤다.)
 *
 * 한 건이 실패해도 나머지는 돌도록 각각 따로 처리한다.
 */
@Component
public class MaintenanceScheduler {

    private static final Logger log = LoggerFactory.getLogger(MaintenanceScheduler.class);

    /** 이 기간 손대지 않은 비회원 장바구니는 지운다. */
    private static final Duration GUEST_CART_KEEP = Duration.ofDays(30);

    private final CartRepository cartRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final MemberRepository memberRepository;

    public MaintenanceScheduler(CartRepository cartRepository,
                                RefreshTokenRepository refreshTokenRepository,
                                MemberRepository memberRepository) {
        this.cartRepository = cartRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.memberRepository = memberRepository;
    }

    @Scheduled(cron = "0 30 4 * * *", zone = "Asia/Seoul")
    public void cleanUp() {
        runQuietly("오래된 비회원 장바구니", this::deleteStaleGuestCarts);
        runQuietly("만료된 로그인 토큰", this::deleteExpiredRefreshTokens);
        runQuietly("보존기간이 지난 탈퇴 회원", this::purgeWithdrawnMembers);
    }

    @Transactional
    public int deleteStaleGuestCarts() {
        return cartRepository.deleteStaleGuestCarts(Instant.now().minus(GUEST_CART_KEEP));
    }

    @Transactional
    public int deleteExpiredRefreshTokens() {
        refreshTokenRepository.deleteExpired(Instant.now());
        return -1; // 삭제 건수를 돌려주지 않는 쿼리다
    }

    @Transactional
    public int purgeWithdrawnMembers() {
        return memberRepository.purgeWithdrawn(Instant.now());
    }

    private void runQuietly(String what, java.util.function.IntSupplier job) {
        try {
            int affected = job.getAsInt();
            if (affected > 0) {
                log.info("정리: {} {}건", what, affected);
            }
        } catch (Exception e) {
            // 정리 작업이 실패해도 서비스는 계속 떠 있어야 한다. 로그만 남기고 넘어간다.
            log.warn("정리 실패: {} - {}", what, e.getMessage());
        }
    }
}
