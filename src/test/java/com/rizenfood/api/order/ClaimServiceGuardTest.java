package com.rizenfood.api.order;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.rizenfood.api.order.dto.ClaimDtos;
import com.rizenfood.api.payment.PaymentGateway;
import com.rizenfood.api.payment.PaymentRepository;
import com.rizenfood.api.product.ProductOptionRepository;
import com.rizenfood.api.product.ProductRepository;

/**
 * 취소·반품 요청이 재고를 두 번 돌려주지 않는지 (CLAUDE.md 규칙 5).
 *
 * 사용자 시뮬레이션에서 같은 주문에 취소 신청이 두 건 들어가는 것을 발견했다.
 * 두 건을 모두 "완료" 처리하면, 또는 한 건을 두 번 "완료" 처리하면 재고가 두 번 늘어나고
 * 환불도 두 번 시도된다. 그 경로를 전부 막았는지 확인한다.
 */
class ClaimServiceGuardTest {

    private OrderRepository orderRepository;
    private OrderClaimRepository claimRepository;
    private ProductRepository productRepository;
    private ProductOptionRepository optionRepository;
    private PaymentGateway paymentGateway;
    private ClaimService service;

    @BeforeEach
    void setUp() {
        orderRepository = mock(OrderRepository.class);
        claimRepository = mock(OrderClaimRepository.class);
        productRepository = mock(ProductRepository.class);
        optionRepository = mock(ProductOptionRepository.class);
        paymentGateway = mock(PaymentGateway.class);
        service = new ClaimService(orderRepository, claimRepository, mock(PaymentRepository.class),
                productRepository, optionRepository, mock(StockLedgerRepository.class), paymentGateway,
                mock(com.rizenfood.api.coupon.CouponService.class));
    }

    private Order order(String status) {
        Order o = new Order();
        o.setOrderNo("R20260916-TEST000001");
        o.setTotalAmount(12_900);
        ReflectionTestUtils.setField(o, "id", 10L);
        ReflectionTestUtils.setField(o, "status", status);
        o.addItem(new OrderItem(1L, null, "크림오브라이스", null, null, 12_900, 1));
        return o;
    }

    private OrderClaim claim(String status) {
        OrderClaim c = new OrderClaim(10L, OrderClaim.Type.CANCEL, "CHANGE_MIND", null);
        ReflectionTestUtils.setField(c, "id", 3L);
        ReflectionTestUtils.setField(c, "status", status);
        return c;
    }

    @Test
    @DisplayName("처리 중인 요청이 있으면 같은 주문에 새 요청을 받지 않는다")
    void rejectsDuplicateOpenClaim() {
        when(orderRepository.findByOrderNo("R20260916-TEST000001")).thenReturn(Optional.of(order("PAID")));
        when(claimRepository.existsByOrderIdAndStatusIn(anyLong(), any())).thenReturn(true);

        assertThatThrownBy(() -> service.create("R20260916-TEST000001", null,
                new ClaimDtos.CreateRequest("CANCEL", "CHANGE_MIND", "두 번째 신청")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("이미 접수된 요청");

        verify(claimRepository, never()).save(any());
    }

    @Test
    @DisplayName("이미 완료된 요청을 다시 완료해도 재고·환불이 반복되지 않는다")
    void completedClaimCannotBeProcessedAgain() {
        when(claimRepository.findById(3L)).thenReturn(Optional.of(claim("COMPLETED")));
        when(orderRepository.findById(10L)).thenReturn(Optional.of(order("CANCELLED")));

        assertThatThrownBy(() -> service.process(3L,
                new ClaimDtos.ProcessRequest("COMPLETED", null, null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("이미 처리가 끝난");

        verify(productRepository, never()).increaseStock(anyLong(), anyInt());
        verify(paymentGateway, never()).cancel(any(), any(), any());
    }

    @Test
    @DisplayName("이미 취소된 주문의 다른 요청을 완료해도 재고를 또 돌려주지 않는다")
    void alreadyCancelledOrderIsNotRestockedTwice() {
        when(claimRepository.findById(3L)).thenReturn(Optional.of(claim("REQUESTED")));
        when(orderRepository.findById(10L)).thenReturn(Optional.of(order("CANCELLED")));

        assertThatThrownBy(() -> service.process(3L,
                new ClaimDtos.ProcessRequest("COMPLETED", null, null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("이미 취소·환불");

        verify(productRepository, never()).increaseStock(anyLong(), anyInt());
    }
}
