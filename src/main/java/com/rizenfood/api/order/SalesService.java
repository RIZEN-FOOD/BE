package com.rizenfood.api.order;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
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
 *   결제 — 결제일에 매출로 잡는다. 나중에 취소돼도 결제일 매출은 그대로 두고,
 *   환불 — 취소·반품 환불이 끝난 날(처리일)에 뺀다.
 * 그래서 두 기간에 걸친 주문도 각 기간 합계가 맞는다.
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
    private final OrderClaimRepository claimRepository;
    private final ProductRepository productRepository;

    public SalesService(OrderRepository orderRepository,
                        OrderClaimRepository claimRepository,
                        ProductRepository productRepository) {
        this.orderRepository = orderRepository;
        this.claimRepository = claimRepository;
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
        List<OrderClaim> refunds = claimRepository.findRefundsBetween(start, end);

        Map<Long, Order> refundOrders = new HashMap<>();
        Set<Long> refundOrderIds = new HashSet<>();
        refunds.forEach(c -> refundOrderIds.add(c.getOrderId()));
        if (!refundOrderIds.isEmpty()) {
            orderRepository.findWithItemsByIdIn(refundOrderIds).forEach(o -> refundOrders.put(o.getId(), o));
        }

        Predicate<OrderItem> isTaxFree = taxFreeRule(paid, refundOrders.values());

        List<SalesDtos.Line> lines = new ArrayList<>();
        int taxFreeSales = 0, taxableGoods = 0, shipping = 0, discount = 0;
        for (Order o : paid) {
            TaxSplit s = TaxSplit.of(o, isTaxFree);
            taxFreeSales += s.taxFree();
            taxableGoods += s.taxableGoods();
            shipping += s.shippingFee();
            discount += o.getDiscountAmount();
            lines.add(new SalesDtos.Line("PAID", o.getPaidAt(), o.getOrderNo(), o.getStatus(),
                    s.taxFree(), s.taxableGoods(), s.shippingFee(), s.taxable(), o.getTotalAmount()));
        }

        int refundCount = 0, taxFreeRefund = 0, taxableRefund = 0;
        for (OrderClaim c : refunds) {
            Order o = refundOrders.get(c.getOrderId());
            // 결제 전 취소는 돌려준 돈이 없다 (요청에 금액이 적혀 있어도).
            if (o == null || o.getPaidAt() == null || !Order.Channel.MALL.name().equals(o.getChannel())) {
                continue;
            }
            TaxSplit.Refund r = TaxSplit.of(o, isTaxFree).refund(o.getTotalAmount(), c.getRefundAmount());
            refundCount++;
            taxFreeRefund += r.taxFree();
            taxableRefund += r.taxable();
            lines.add(new SalesDtos.Line("REFUND", c.getProcessedAt(), o.getOrderNo(), o.getStatus(),
                    -r.taxFree(), null, null, -r.taxable(), -(r.taxFree() + r.taxable())));
        }
        lines.sort(Comparator.comparing(SalesDtos.Line::at, Comparator.nullsLast(Comparator.naturalOrder())));

        int taxFreeNet = taxFreeSales - taxFreeRefund;
        int taxableNet = taxableGoods + shipping - taxableRefund;
        int supply = (int) Math.round(taxableNet / 1.1);
        SalesDtos.Totals totals = new SalesDtos.Totals(
                paid.size(), taxFreeSales, taxableGoods, shipping, discount,
                refundCount, taxFreeRefund, taxableRefund,
                taxFreeNet, taxableNet, supply, taxableNet - supply, taxFreeNet + taxableNet);
        return new SalesDtos.Report(from, to, totals, lines, false);
    }

    /**
     * 주문 상품이 면세인가. 스냅샷이 있으면 그대로, 없으면(V40 전 주문) 상품의 지금 설정.
     * 상품이 지워졌으면 과세로 본다.
     */
    private Predicate<OrderItem> taxFreeRule(List<Order> paid, java.util.Collection<Order> refunded) {
        Set<Long> unknown = new HashSet<>();
        for (List<Order> group : List.of(paid, List.copyOf(refunded))) {
            for (Order o : group) {
                for (OrderItem it : o.getItems()) {
                    if (it.getTaxFree() == null && it.getProductId() != null) {
                        unknown.add(it.getProductId());
                    }
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
