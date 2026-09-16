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

    /** 아직 끝나지 않은 요청 상태. */
    private static final List<String> OPEN_STATUSES = List.of(
            OrderClaim.Status.REQUESTED.name(), OrderClaim.Status.APPROVED.name());

    private final OrderRepository orderRepository;
    private final OrderClaimRepository claimRepository;
    private final PaymentRepository paymentRepository;
    private final ProductRepository productRepository;
    private final ProductOptionRepository optionRepository;
    private final StockLedgerRepository stockLedgerRepository;
    /** 취소·반품 완료 시 PG 환불 요청용 (포트원 또는 모의). */
    private final com.rizenfood.api.payment.PaymentGateway paymentGateway;

    public ClaimService(OrderRepository orderRepository,
                        OrderClaimRepository claimRepository,
                        PaymentRepository paymentRepository,
                        ProductRepository productRepository,
                        ProductOptionRepository optionRepository,
                        StockLedgerRepository stockLedgerRepository,
                        com.rizenfood.api.payment.PaymentGateway paymentGateway) {
        this.orderRepository = orderRepository;
        this.claimRepository = claimRepository;
        this.paymentRepository = paymentRepository;
        this.productRepository = productRepository;
        this.optionRepository = optionRepository;
        this.stockLedgerRepository = stockLedgerRepository;
        this.paymentGateway = paymentGateway;
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

        // 처리 중인 요청이 있으면 새로 받지 않는다. 같은 주문에 취소가 두 건 쌓이면
        // 둘 다 완료 처리될 때 재고가 두 번 돌아온다.
        if (claimRepository.existsByOrderIdAndStatusIn(order.getId(), OPEN_STATUSES)) {
            throw new IllegalArgumentException("이미 접수된 요청이 있습니다. 처리 결과를 기다려 주세요.");
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

        // 끝난 요청을 다시 처리하지 않는다 (재고 이중 복구·이중 환불 방지).
        if (!claim.isOpen()) {
            throw new IllegalArgumentException("이미 처리가 끝난 요청입니다.");
        }

        Integer refund = req.refundAmount();
        // 결제액을 넘는 환불은 PG 가 거부한다. 우리 기록만 어긋나지 않게 미리 막는다.
        if (refund != null && (refund <= 0 || refund > order.getTotalAmount())) {
            throw new IllegalArgumentException("환불 금액은 1원 이상, 결제 금액 이하여야 합니다.");
        }
        // 취소·반품을 완료 처리하면 재고를 되돌리고 주문·결제를 정리한다.
        if (target == OrderClaim.Status.COMPLETED
                && (claim.getType().equals("CANCEL") || claim.getType().equals("RETURN"))) {
            // 이미 취소·환불된 주문이면 재고를 또 돌려주지 않는다.
            if (order.getStatus().equals("CANCELLED") || order.getStatus().equals("REFUNDED")) {
                throw new IllegalArgumentException("이미 취소·환불이 끝난 주문입니다.");
            }
            restock(order);
            String nextOrderStatus = claim.getType().equals("CANCEL") ? "CANCELLED" : "REFUNDED";
            order.applyStatus(nextOrderStatus);
            if (refund == null) {
                refund = order.getTotalAmount();
            }
            // ★ 결제된 주문이면 PG 에 실제 환불을 요청한다. 실패하면 예외 → 트랜잭션 롤백으로
            //   재고·주문 상태 변경까지 되돌려, "환불은 안 됐는데 취소됨" 상태를 만들지 않는다.
            final int refundAmount = refund;
            final String reason = claim.getType().equals("CANCEL") ? "고객 요청 주문 취소" : "반품 환불";
            paymentRepository.findByOrderId(order.getId())
                    .ifPresent(p -> cancelPayment(order, p, refundAmount, reason));
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

    /**
     * 결제 취소(환불). 결제 완료된 건만 PG 에 환불을 요청한다.
     * 결제 전 주문은 PG 에 되돌릴 돈이 없으므로 상태만 정리한다.
     */
    private void cancelPayment(Order order, Payment p, int refundAmount, String reason) {
        if (!Payment.Status.PAID.name().equals(p.getStatus())) {
            p.markFailed(reason);
            return;
        }
        try {
            paymentGateway.cancel(order.getOrderNo(), refundAmount, reason);
        } catch (com.rizenfood.api.payment.PaymentGateway.PaymentException e) {
            // 400 으로 관리자 화면에 사유를 보여주고, 트랜잭션은 롤백된다(재고·상태 변경 없음).
            throw new IllegalArgumentException("PG 환불 요청이 실패해 처리하지 않았습니다. " + e.getMessage());
        }
        p.markCancelled(refundAmount < p.getAmount());
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
