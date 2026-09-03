package com.rizenfood.api.order;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.rizenfood.api.common.NotFoundException;
import com.rizenfood.api.order.dto.ClaimDtos;
import com.rizenfood.api.payment.Payment;
import com.rizenfood.api.payment.PaymentRepository;
import com.rizenfood.api.product.ProductOptionRepository;
import com.rizenfood.api.product.ProductRepository;

/**
 * 취소·반품·교환.
 *
 * ★ 요청 시각·처리 시각을 반드시 남긴다 (엔티티가 자동 기록, CLAUDE.md §7).
 *   승인 완료 시 재고를 원복하고(취소·반품) 주문·결제 상태를 정리한다.
 */
@Service
public class ClaimService {

    private final OrderRepository orderRepository;
    private final OrderClaimRepository claimRepository;
    private final PaymentRepository paymentRepository;
    private final ProductRepository productRepository;
    private final ProductOptionRepository optionRepository;
    private final StockLedgerRepository stockLedgerRepository;

    public ClaimService(OrderRepository orderRepository,
                        OrderClaimRepository claimRepository,
                        PaymentRepository paymentRepository,
                        ProductRepository productRepository,
                        ProductOptionRepository optionRepository,
                        StockLedgerRepository stockLedgerRepository) {
        this.orderRepository = orderRepository;
        this.claimRepository = claimRepository;
        this.paymentRepository = paymentRepository;
        this.productRepository = productRepository;
        this.optionRepository = optionRepository;
        this.stockLedgerRepository = stockLedgerRepository;
    }

    // ── 고객 ──────────────────────────────────────────────────

    @Transactional
    public ClaimDtos.View create(String orderNo, Long memberId, ClaimDtos.CreateRequest req) {
        Order order = loadOwned(orderNo, memberId);
        OrderClaim.Type type = parseType(req.type());

        // 상태별 신청 가능 여부.
        String st = order.getStatus();
        if (type == OrderClaim.Type.CANCEL) {
            if (!(st.equals("PENDING") || st.equals("PAID") || st.equals("PREPARING"))) {
                throw new IllegalArgumentException("배송이 시작된 주문은 취소할 수 없습니다. 반품을 신청해 주세요.");
            }
        } else { // RETURN, EXCHANGE
            if (!(st.equals("SHIPPED") || st.equals("DELIVERED"))) {
                throw new IllegalArgumentException("배송 완료 후 신청할 수 있습니다.");
            }
        }

        OrderClaim claim = claimRepository.save(
                new OrderClaim(order.getId(), type, req.reasonCode(), blankToNull(req.reasonText())));
        return toView(claim, order.getOrderNo());
    }

    @Transactional(readOnly = true)
    public List<ClaimDtos.View> listForOrder(String orderNo, Long memberId) {
        Order order = loadOwned(orderNo, memberId);
        return claimRepository.findByOrderIdOrderByRequestedAtDesc(order.getId()).stream()
                .map(c -> toView(c, order.getOrderNo())).toList();
    }

    // ── 관리자 ────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Page<ClaimDtos.AdminItem> adminList(String status, Pageable pageable) {
        Page<OrderClaim> page = (status == null || status.isBlank())
                ? claimRepository.findAllByOrderByRequestedAtDesc(pageable)
                : claimRepository.findByStatusOrderByRequestedAtDesc(status, pageable);
        return page.map(c -> {
            Order o = orderRepository.findById(c.getOrderId()).orElse(null);
            return new ClaimDtos.AdminItem(
                    c.getId(),
                    o != null ? o.getOrderNo() : null,
                    o != null ? o.getOrdererName() : null,
                    c.getType(), c.getReasonCode(), c.getStatus(),
                    o != null ? o.getTotalAmount() : 0,
                    c.getRequestedAt(), c.getProcessedAt());
        });
    }

    @Transactional
    public ClaimDtos.View process(Long claimId, ClaimDtos.ProcessRequest req) {
        OrderClaim.Status target = parseStatus(req.status());
        OrderClaim claim = claimRepository.findById(claimId)
                .orElseThrow(() -> new NotFoundException("요청을 찾을 수 없습니다."));
        Order order = orderRepository.findById(claim.getOrderId())
                .orElseThrow(() -> new NotFoundException("주문을 찾을 수 없습니다."));

        Integer refund = req.refundAmount();
        // 취소·반품을 완료 처리하면 재고를 되돌리고 주문·결제를 정리한다.
        if (target == OrderClaim.Status.COMPLETED
                && (claim.getType().equals("CANCEL") || claim.getType().equals("RETURN"))) {
            restock(order);
            String nextOrderStatus = claim.getType().equals("CANCEL") ? "CANCELLED" : "REFUNDED";
            order.applyStatus(nextOrderStatus);
            paymentRepository.findByOrderId(order.getId())
                    .ifPresent(p -> cancelPayment(p));
            if (refund == null) {
                refund = order.getTotalAmount();
            }
        }

        claim.process(target, blankToNull(req.adminMemo()), refund);
        return toView(claim, order.getOrderNo());
    }

    // ── 내부 ──────────────────────────────────────────────────

    private void restock(Order order) {
        for (OrderItem it : order.getItems()) {
            if (it.getProductId() == null) {
                continue; // 상품이 삭제된 경우 원복 대상 없음
            }
            int qty = it.getQuantity();
            Integer balance;
            if (it.getProductOptionId() != null) {
                optionRepository.increaseStock(it.getProductOptionId(), qty);
                balance = optionRepository.currentStock(it.getProductOptionId());
            } else {
                productRepository.increaseStock(it.getProductId(), qty);
                balance = productRepository.currentStock(it.getProductId());
            }
            stockLedgerRepository.save(new StockLedger(
                    it.getProductId(), it.getProductOptionId(),
                    qty, balance != null ? balance : 0,
                    StockLedger.Reason.CANCEL, order.getId()));
        }
    }

    private void cancelPayment(Payment p) {
        // 실제 PG 라면 여기서 승인 취소 API 를 호출한다. 지금은 모의로 상태만 정리.
        p.markFailed("취소·반품 처리에 따른 결제 취소");
    }

    private Order loadOwned(String orderNo, Long memberId) {
        Order order = orderRepository.findByOrderNo(orderNo)
                .orElseThrow(() -> new NotFoundException("주문을 찾을 수 없습니다."));
        if (order.getMemberId() != null
                && (memberId == null || !order.getMemberId().equals(memberId))) {
            throw new NotFoundException("주문을 찾을 수 없습니다.");
        }
        return order;
    }

    private ClaimDtos.View toView(OrderClaim c, String orderNo) {
        return new ClaimDtos.View(c.getId(), orderNo, c.getType(), c.getReasonCode(),
                c.getReasonText(), c.getStatus(), c.getRefundAmount(), c.getAdminMemo(),
                c.getRequestedAt(), c.getProcessedAt());
    }

    private OrderClaim.Type parseType(String v) {
        try {
            return OrderClaim.Type.valueOf(v);
        } catch (Exception e) {
            throw new IllegalArgumentException("요청 종류가 올바르지 않습니다.");
        }
    }

    private OrderClaim.Status parseStatus(String v) {
        OrderClaim.Status s;
        try {
            s = OrderClaim.Status.valueOf(v);
        } catch (Exception e) {
            throw new IllegalArgumentException("처리 상태가 올바르지 않습니다.");
        }
        if (s == OrderClaim.Status.REQUESTED) {
            throw new IllegalArgumentException("처리 상태를 선택해 주세요.");
        }
        return s;
    }

    private String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s;
    }
}
