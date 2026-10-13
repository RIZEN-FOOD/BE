package com.rizenfood.api.order;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.rizenfood.api.order.dto.SalesDtos;
import com.rizenfood.api.product.Product;
import com.rizenfood.api.product.ProductRepository;

/**
 * 관리자 [매출] — 기간별 과세·면세 매출 (2026-10-12).
 *
 * 부가세 신고 때 면세 매출(예: 플레인)과 과세 매출을 나눠야 해서 만들었다.
 * 결제일에 매출로 잡는다.
 *
 * ★ 취소·환불된 주문은 매출에 넣지 않는다 (2026-10-13 대표 확인). 결제일과 취소일이 다른 달이어도
 *   결제한 달에서 빠진다 — 지금 상태가 취소·환불이면 그 주문은 없던 것으로 본다.
 * ★ 배송비는 면세 상품만 산 주문이면 면세, 과세 상품이 섞이면 과세다 (TaxSplit).
 *
 * 자사몰(MALL) 주문만 센다. 스마트스토어 매출은 네이버 정산 자료로 따로 신고한다.
 * 면세 여부는 주문 상품의 스냅샷을 따르고, 구분 도입(V40) 전 주문은 상품의 지금 설정을 따른다.
 */
@Service
public class SalesService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    /** 한 번에 볼 수 있는 최대 기간. 1년치 신고까지 한 번에 받을 수 있게. */
    static final int MAX_DAYS = 366;

    /** 화면에 보내는 최대 내역 줄 수. 엑셀에는 전부 들어간다. */
    static final int MAX_SCREEN_LINES = 1000;

    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;

    public SalesService(OrderRepository orderRepository, ProductRepository productRepository) {
        this.orderRepository = orderRepository;
        this.productRepository = productRepository;
    }

    /** 화면용 — 내역은 앞부분만. */
    @Transactional(readOnly = true)
    public SalesDtos.Report report(LocalDate from, LocalDate to) {
        SalesDtos.Report full = build(from, to);
        if (full.lines().size() <= MAX_SCREEN_LINES) {
            return full;
        }
        return new SalesDtos.Report(full.from(), full.to(), full.totals(),
                full.lines().subList(0, MAX_SCREEN_LINES), true);
    }

    /** 엑셀 — 내역 전부. */
    @Transactional(readOnly = true)
    public byte[] export(LocalDate from, LocalDate to) {
        return SalesSheet.build(build(from, to));
    }

    private SalesDtos.Report build(LocalDate from, LocalDate to) {
        if (from == null || to == null) {
            throw new IllegalArgumentException("기간을 골라 주세요.");
        }
        if (to.isBefore(from)) {
            throw new IllegalArgumentException("끝 날짜가 시작 날짜보다 앞입니다.");
        }
        if (ChronoUnit.DAYS.between(from, to) >= MAX_DAYS) {
            throw new IllegalArgumentException("한 번에 1년(366일)까지 볼 수 있습니다.");
        }
        Instant start = from.atStartOfDay(KST).toInstant();
        Instant end = to.plusDays(1).atStartOfDay(KST).toInstant();

        List<Order> paid = orderRepository.findPaidBetween(start, end);
        int excluded = (int) orderRepository.countCancelledPaidBetween(start, end);
        Predicate<OrderItem> isTaxFree = taxFreeRule(paid);

        List<SalesDtos.Line> lines = new ArrayList<>();
        int freeGoods = 0, freeShip = 0, taxGoods = 0, taxShip = 0, discount = 0;
        for (Order o : paid) {
            TaxSplit s = TaxSplit.of(o, isTaxFree);
            freeGoods += s.taxFreeGoods();
            taxGoods += s.taxableGoods();
            if (s.shippingTaxFree()) {
                freeShip += s.shippingFee();
            } else {
                taxShip += s.shippingFee();
            }
            discount += o.getDiscountAmount();
            lines.add(new SalesDtos.Line(o.getPaidAt(), o.getOrderNo(), o.getStatus(),
                    s.taxFreeGoods(), s.taxableGoods(), s.shippingFee(), s.shippingTaxFree(),
                    s.taxFree(), s.taxable(), o.getTotalAmount()));
        }

        int taxFree = freeGoods + freeShip;
        int taxable = taxGoods + taxShip;
        int supply = (int) Math.round(taxable / 1.1);
        SalesDtos.Totals totals = new SalesDtos.Totals(
                paid.size(), excluded, freeGoods, freeShip, taxGoods, taxShip, discount,
                taxFree, taxable, supply, taxable - supply, taxFree + taxable);
        return new SalesDtos.Report(from, to, totals, lines, false);
    }

    /**
     * 주문 상품이 면세인가. 스냅샷이 있으면 그대로, 없으면(V40 전 주문) 상품의 지금 설정.
     * 상품이 지워졌으면 과세로 본다.
     */
    private Predicate<OrderItem> taxFreeRule(List<Order> orders) {
        Set<Long> unknown = new HashSet<>();
        for (Order o : orders) {
            for (OrderItem it : o.getItems()) {
                if (it.getTaxFree() == null && it.getProductId() != null) {
                    unknown.add(it.getProductId());
                }
            }
        }
        Map<Long, Boolean> current = new HashMap<>();
        if (!unknown.isEmpty()) {
            for (Product p : productRepository.findAllById(unknown)) {
                current.put(p.getId(), p.isTaxFree());
            }
        }
        return it -> it.getTaxFree() != null
                ? it.getTaxFree()
                : it.getProductId() != null && current.getOrDefault(it.getProductId(), false);
    }
}
