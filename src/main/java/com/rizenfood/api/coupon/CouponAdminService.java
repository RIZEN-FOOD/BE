package com.rizenfood.api.coupon;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.rizenfood.api.common.NotFoundException;
import com.rizenfood.api.order.OrderRepository;

/**
 * 관리자 할인코드 등록·수정·집계.
 *
 * 코드별 사용 건수와 매출은 orders 를 coupon_id 로 묶어서 낸다.
 * coupon.used_count 는 남은 수량을 막는 값이라 집계에 쓰지 않는다 — 취소로 되돌아가면
 * "몇 장 나갔나"와 "얼마 팔렸나"가 어긋나기 때문이다.
 */
@Service
public class CouponAdminService {

    private final CouponRepository couponRepository;
    private final OrderRepository orderRepository;

    public CouponAdminService(CouponRepository couponRepository, OrderRepository orderRepository) {
        this.couponRepository = couponRepository;
        this.orderRepository = orderRepository;
    }

    @Transactional(readOnly = true)
    public List<CouponDtos.AdminItem> list() {
        Map<Long, long[]> stats = loadStats();
        List<CouponDtos.AdminItem> out = new ArrayList<>();
        for (Coupon c : couponRepository.findAll(Sort.by(Sort.Direction.DESC, "id"))) {
            out.add(toItem(c, stats));
        }
        return out;
    }

    @Transactional(readOnly = true)
    public CouponDtos.AdminItem detail(Long id) {
        Coupon c = couponRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("없는 할인코드입니다."));
        return toItem(c, loadStats());
    }

    @Transactional
    public CouponDtos.AdminItem create(CouponDtos.SaveRequest req) {
        String code = CouponService.normalize(req.code());
        if (couponRepository.existsByCode(code)) {
            throw new IllegalArgumentException("이미 쓰고 있는 코드입니다. 다른 코드를 넣어 주세요.");
        }
        Coupon c = new Coupon();
        apply(c, req, code);
        couponRepository.save(c);
        return toItem(c, Map.of());
    }

    @Transactional
    public CouponDtos.AdminItem update(Long id, CouponDtos.SaveRequest req) {
        Coupon c = couponRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("없는 할인코드입니다."));
        String code = CouponService.normalize(req.code());
        couponRepository.findByCode(code)
                .filter(other -> !other.getId().equals(id))
                .ifPresent(other -> {
                    throw new IllegalArgumentException("이미 쓰고 있는 코드입니다. 다른 코드를 넣어 주세요.");
                });
        apply(c, req, code);
        c.touch();
        return toItem(c, loadStats());
    }

    /**
     * 삭제. 이미 쓰인 코드는 지우지 않는다 — 주문 기록에서 이름이 사라지면
     * 나중에 집계를 다시 볼 수 없다. 대신 꺼두면 즉시 사용이 막힌다.
     */
    @Transactional
    public void delete(Long id) {
        Coupon c = couponRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("없는 할인코드입니다."));
        long used = loadStats().getOrDefault(id, new long[] {0, 0, 0})[0];
        if (used > 0) {
            throw new IllegalArgumentException(
                    "이미 " + used + "건에 쓰인 코드라 지울 수 없습니다. 노출을 꺼주세요.");
        }
        couponRepository.delete(c);
    }

    // ── 안쪽 ──────────────────────────────────────────────────

    private void apply(Coupon c, CouponDtos.SaveRequest req, String code) {
        if (req.endAt().isBefore(req.startAt()) || req.endAt().equals(req.startAt())) {
            throw new IllegalArgumentException("종료 일시가 시작 일시보다 뒤여야 합니다.");
        }
        if (Coupon.PERCENT.equals(req.discountType()) && req.discountValue() > 100) {
            throw new IllegalArgumentException("비율 할인은 100%를 넘을 수 없습니다.");
        }
        c.setName(req.name().trim());
        c.setCode(code);
        c.setDiscountType(req.discountType());
        c.setDiscountValue(req.discountValue());
        c.setMaxDiscount(Coupon.PERCENT.equals(req.discountType()) ? req.maxDiscount() : null);
        c.setMinOrderAmount(req.minOrderAmount());
        c.setTotalQuantity(req.totalQuantity());
        c.setPerMemberLimit(req.perMemberLimit());
        c.setStartAt(req.startAt());
        c.setEndAt(req.endAt());
        c.setVisible(req.visible());
    }

    /** couponId → {주문건수, 매출합, 할인합} */
    private Map<Long, long[]> loadStats() {
        Map<Long, long[]> map = new HashMap<>();
        for (Object[] row : orderRepository.summarizeByCoupon()) {
            Long id = (Long) row[0];
            map.put(id, new long[] {
                    ((Number) row[1]).longValue(),
                    ((Number) row[2]).longValue(),
                    ((Number) row[3]).longValue()});
        }
        return map;
    }

    private CouponDtos.AdminItem toItem(Coupon c, Map<Long, long[]> stats) {
        long[] s = stats.getOrDefault(c.getId(), new long[] {0, 0, 0});
        return new CouponDtos.AdminItem(
                c.getId(), c.getName(), c.getCode(),
                c.getDiscountType(), c.getDiscountValue(), c.getMaxDiscount(),
                c.getMinOrderAmount(), c.getTotalQuantity(), c.getUsedCount(),
                c.getPerMemberLimit(), c.getStartAt(), c.getEndAt(), c.isVisible(),
                state(c), (int) s[0], s[1], s[2]);
    }

    /** 관리자 목록에 그대로 띄울 상태 한마디. */
    private String state(Coupon c) {
        Instant now = Instant.now();
        if (!c.isVisible()) return "꺼짐";
        if (c.getStartAt() != null && now.isBefore(c.getStartAt())) return "시작 전";
        if (c.getEndAt() != null && !now.isBefore(c.getEndAt())) return "끝남";
        if (c.getTotalQuantity() != null && c.getUsedCount() >= c.getTotalQuantity()) return "소진";
        return "사용 중";
    }
}
