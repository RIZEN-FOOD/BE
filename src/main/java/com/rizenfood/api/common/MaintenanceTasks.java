package com.rizenfood.api.common;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.rizenfood.api.cart.CartRepository;
import com.rizenfood.api.member.MemberRepository;
import com.rizenfood.api.member.RefreshTokenRepository;
import com.rizenfood.api.order.Delivery;
import com.rizenfood.api.order.DeliveryRepository;
import com.rizenfood.api.order.Order;
import com.rizenfood.api.order.OrderRepository;
import com.rizenfood.api.setting.SiteSetting;
import com.rizenfood.api.setting.SiteSettingRepository;

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

    /** 자동 처리 일수를 아무리 크게 적어도 이 이상은 기다리지 않는다(오타 방어). */
    static final int MAX_AUTO_COMPLETE_DAYS = 60;

    private final CartRepository cartRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final MemberRepository memberRepository;
    private final DeliveryRepository deliveryRepository;
    private final OrderRepository orderRepository;
    private final SiteSettingRepository siteSettingRepository;

    public MaintenanceTasks(CartRepository cartRepository,
                            RefreshTokenRepository refreshTokenRepository,
                            MemberRepository memberRepository,
                            DeliveryRepository deliveryRepository,
                            OrderRepository orderRepository,
                            SiteSettingRepository siteSettingRepository) {
        this.cartRepository = cartRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.memberRepository = memberRepository;
        this.deliveryRepository = deliveryRepository;
        this.orderRepository = orderRepository;
        this.siteSettingRepository = siteSettingRepository;
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

    /**
     * 발송한 지 오래된 주문을 배송 완료로 바꾼다.
     *
     * 택배 추적을 붙이지 않았으므로 실제 도착 시각은 알 수 없다. 이건 화면 표시를 위한
     * <b>추정</b>이고, 관리자가 언제든 직접 고칠 수 있다.
     *
     * ★ 청약철회 기간은 손님이 실제로 물건을 받은 날부터 센다. 여기서 찍는 날짜가
     *   그 기산일을 대신하지 않는다.
     * ★ 취소·반품이 진행 중이어서 주문이 배송중이 아니게 된 건은 건드리지 않는다.
     */
    @Transactional
    public int completeOldDeliveries() {
        int days = autoCompleteDays();
        if (days <= 0) {
            return 0; // 자동 처리를 꺼 둔 상태
        }
        Instant before = Instant.now().minus(days, ChronoUnit.DAYS);
        List<Delivery> targets =
                deliveryRepository.findTop500ByStatusAndShippedAtBefore(Delivery.Status.SHIPPED.name(), before);

        int changed = 0;
        for (Delivery delivery : targets) {
            Order order = orderRepository.findById(delivery.getOrderId()).orElse(null);
            if (order == null || !Order.Status.SHIPPED.name().equals(order.getStatus())) {
                continue;
            }
            delivery.markDelivered();
            order.applyStatus(Order.Status.DELIVERED.name());
            changed++;
        }
        return changed;
    }

    /** 설정값. 비었거나 숫자가 아니면 자동 처리를 하지 않는다 — 임의로 기본값을 쓰지 않는다. */
    private int autoCompleteDays() {
        String raw = siteSettingRepository.findById("shipping.auto_complete_days")
                .map(SiteSetting::getValue)
                .map(String::trim)
                .orElse("");
        try {
            return Math.min(Integer.parseInt(raw), MAX_AUTO_COMPLETE_DAYS);
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
