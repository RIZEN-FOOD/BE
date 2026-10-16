package com.rizenfood.api.notify;

import java.text.NumberFormat;
import java.util.Locale;
import java.util.Optional;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.rizenfood.api.order.Order;
import com.rizenfood.api.order.OrderRepository;

/**
 * 운영자 알림 문자 본문 (2026-10-16).
 *
 * ★ 문자는 통신사를 거쳐 가므로 손님 이름·연락처·주소는 넣지 않는다. 주문번호로 관리자에서 찾아 본다.
 */
@Component
class AdminNotificationBuilder {

    private final OrderRepository orderRepository;

    AdminNotificationBuilder(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    /** 새 주문(결제 완료). 자사몰 주문이 아니면 보내지 않는다. */
    @Transactional(readOnly = true)
    public Optional<String> newOrder(Long orderId) {
        return orderRepository.findById(orderId)
                .filter(o -> Order.Channel.MALL.name().equals(o.getChannel()))
                .map(o -> "[라이즌푸드] 새 주문\n"
                        + "주문번호 " + o.getOrderNo() + "\n"
                        + "상품 " + OrderNotificationBuilder.productSummary(o.getItems()) + "\n"
                        + "결제 " + won(o.getTotalAmount()) + "원");
    }

    /** 취소·반품·교환 요청. */
    @Transactional(readOnly = true)
    public Optional<String> claimRequested(Long orderId, String type) {
        String label = switch (type) {
            case "CANCEL" -> "취소";
            case "RETURN" -> "반품";
            case "EXCHANGE" -> "교환";
            default -> "처리";
        };
        return orderRepository.findById(orderId)
                .map(o -> "[라이즌푸드] " + label + " 요청\n"
                        + "주문번호 " + o.getOrderNo() + "\n"
                        + "관리자 [취소·반품·교환]에서 처리해 주세요.");
    }

    private static String won(int amount) {
        return NumberFormat.getNumberInstance(Locale.KOREA).format(amount);
    }
}
