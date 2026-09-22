package com.rizenfood.api.coupon;

import java.time.Instant;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.rizenfood.api.order.OrderRepository;

/**
 * 할인코드 검증과 사용 기록.
 *
 * ★ 할인액은 언제나 서버가 계산한다. 화면이 보낸 금액은 쓰지 않는다 (CLAUDE.md 규칙 5).
 * ★ 남은 수량은 UPDATE ... WHERE used_count < total_quantity 로 원자적으로 깎는다.
 *   먼저 읽고 판단한 뒤 쓰면, 동시에 들어온 주문이 한도를 넘겨 쓸 수 있다.
 * ★ 손님에게 돌려주는 실패 사유는 구체적으로 적는다. "쓸 수 없습니다"만 있으면
 *   최소 주문금액이 모자란 건지 기간이 끝난 건지 몰라 고객센터로 온다.
 */
@Service
public class CouponService {

    /** 코드를 못 쓰는 이유. 화면에 그대로 보여줄 문장을 함께 들고 다닌다. */
    public static class RejectedException extends RuntimeException {
        public RejectedException(String message) { super(message); }
    }

    private final CouponRepository couponRepository;
    private final OrderRepository orderRepository;

    public CouponService(CouponRepository couponRepository, OrderRepository orderRepository) {
        this.couponRepository = couponRepository;
        this.orderRepository = orderRepository;
    }

    /** 입력값 정리. 앞뒤 공백을 버리고 대문자로 맞춘다 — 손님이 소문자로 쳐도 되게. */
    public static String normalize(String raw) {
        if (raw == null) return null;
        String t = raw.trim().toUpperCase();
        return t.isEmpty() ? null : t;
    }

    /**
     * 쓸 수 있는지 보고 할인액을 돌려준다. <b>수량은 잡지 않는다</b> —
     * 결제 화면에서 금액을 미리 보여줄 때 쓰는 경로이기 때문이다.
     *
     * @throws RejectedException 못 쓰는 코드면 이유와 함께
     */
    @Transactional(readOnly = true)
    public Applied check(String rawCode, int itemsAmount, Long memberId, String phoneHash) {
        String code = normalize(rawCode);
        if (code == null) {
            throw new RejectedException("할인코드를 입력해 주세요.");
        }

        Coupon coupon = couponRepository.findByCode(code)
                .orElseThrow(() -> new RejectedException("없는 할인코드입니다. 다시 확인해 주세요."));

        Instant now = Instant.now();
        if (!coupon.isVisible()) {
            throw new RejectedException("지금은 쓸 수 없는 할인코드입니다.");
        }
        if (coupon.getStartAt() != null && now.isBefore(coupon.getStartAt())) {
            throw new RejectedException("아직 시작하지 않은 할인코드입니다.");
        }
        if (coupon.getEndAt() != null && !now.isBefore(coupon.getEndAt())) {
            throw new RejectedException("사용 기간이 끝난 할인코드입니다.");
        }
        if (!coupon.meetsMinimum(itemsAmount)) {
            throw new RejectedException(
                    String.format("%,d원 이상 주문할 때 쓸 수 있습니다.", coupon.getMinOrderAmount()));
        }
        if (coupon.getTotalQuantity() != null && coupon.getUsedCount() >= coupon.getTotalQuantity()) {
            throw new RejectedException("준비된 수량이 모두 사용되었습니다.");
        }
        if (overPersonalLimit(coupon, memberId, phoneHash)) {
            throw new RejectedException("이미 사용하신 할인코드입니다.");
        }

        int discount = coupon.discountFor(itemsAmount);
        if (discount <= 0) {
            throw new RejectedException("이 주문에는 할인이 적용되지 않습니다.");
        }
        return new Applied(coupon.getId(), coupon.getCode(), coupon.getName(), discount);
    }

    /**
     * 주문을 확정하며 한 장을 실제로 쓴다. check() 를 다시 돌린 뒤 수량을 잡는다.
     * 미리보기와 결제 사이에 기간이 끝나거나 수량이 소진될 수 있어서, 여기서 한 번 더 본다.
     *
     * @throws RejectedException 그 사이에 쓸 수 없게 됐으면
     */
    @Transactional
    public Applied use(String rawCode, int itemsAmount, Long memberId, String phoneHash) {
        Applied applied = check(rawCode, itemsAmount, memberId, phoneHash);
        if (couponRepository.take(applied.couponId()) == 0) {
            throw new RejectedException("준비된 수량이 모두 사용되었습니다.");
        }
        return applied;
    }

    /** 주문이 취소되면 쓴 한 장을 되돌린다. 코드가 지워졌으면 아무 일도 하지 않는다. */
    @Transactional
    public void release(Long couponId) {
        if (couponId == null) return;
        couponRepository.giveBack(couponId);
    }

    /** 1인 사용 한도를 넘었나. 회원이면 회원 기준, 비회원이면 주문자 번호 기준으로 센다. */
    private boolean overPersonalLimit(Coupon coupon, Long memberId, String phoneHash) {
        int limit = coupon.getPerMemberLimit();
        if (limit <= 0) return false; // 0 이하면 제한 없음으로 본다

        long used = (memberId != null)
                ? orderRepository.countUsedByMember(coupon.getId(), memberId)
                : (phoneHash != null ? orderRepository.countUsedByPhone(coupon.getId(), phoneHash) : 0);
        return used >= limit;
    }

    @Transactional(readOnly = true)
    public Optional<Coupon> findById(Long id) {
        return couponRepository.findById(id);
    }

    /**
     * 검증을 통과한 결과.
     *
     * @param discount 상품 금액에서 깎을 금액. 배송비는 건드리지 않는다.
     */
    public record Applied(Long couponId, String code, String name, int discount) {
    }
}
