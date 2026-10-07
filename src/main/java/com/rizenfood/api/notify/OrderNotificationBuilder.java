package com.rizenfood.api.notify;

import java.text.NumberFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.rizenfood.api.member.PhoneCipher;
import com.rizenfood.api.order.Delivery;
import com.rizenfood.api.order.DeliveryRepository;
import com.rizenfood.api.order.Order;
import com.rizenfood.api.order.OrderItem;
import com.rizenfood.api.order.OrderRepository;

@Component
class OrderNotificationBuilder {

    private static final Logger log = LoggerFactory.getLogger(OrderNotificationBuilder.class);

    private final OrderRepository orderRepository;
    private final DeliveryRepository deliveryRepository;
    private final PhoneCipher phoneCipher;
    private final AlimtalkProperties properties;
    private final String siteUrl;

    OrderNotificationBuilder(OrderRepository orderRepository, DeliveryRepository deliveryRepository, PhoneCipher phoneCipher, AlimtalkProperties properties, @Value("${app.site-url:}") String siteUrl) {
        this.orderRepository = orderRepository;
        this.deliveryRepository = deliveryRepository;
        this.phoneCipher = phoneCipher;
        this.properties = properties;
        this.siteUrl = siteUrl == null ? "" : siteUrl.trim().replaceAll("/+$", "");
    }

    @Transactional(readOnly = true)
    public Optional<AlimtalkMessage> build(OrderNotificationEvent event) {
        AlimtalkTemplate template = switch (event.type()) {
            case PAID -> AlimtalkTemplate.PAID;
            case SHIPPED -> AlimtalkTemplate.SHIPPED;
            case REFUNDED -> AlimtalkTemplate.REFUNDED;
        };
        String templateCode = properties.templateCode(template);
        if (templateCode.isEmpty()) {
            log.debug("알림톡 템플릿 코드가 없어 보내지 않는다: {}", template);
            return Optional.empty();
        }

        Order order = orderRepository.findById(event.orderId()).orElse(null);
        if (order == null) {
            return Optional.empty();
        }
        if (!Order.Channel.MALL.name().equals(order.getChannel())) {
            return Optional.empty();
        }
        String phone = digits(decrypt(order.getOrdererPhoneEncrypted()));
        if (phone.length() < 10) {
            log.warn("알림톡 받을 번호가 없어 보내지 않는다: 주문 {}", order.getOrderNo());
            return Optional.empty();
        }

        Map<String, String> values = new LinkedHashMap<>();
        values.put("고객명", order.getOrdererName());
        values.put("주문번호", order.getOrderNo());
        values.put("상품명", productSummary(order.getItems()));
        if (template == AlimtalkTemplate.PAID) {
            values.put("결제금액", NumberFormat.getNumberInstance(Locale.KOREA).format(order.getTotalAmount()));
        } else if (template == AlimtalkTemplate.REFUNDED) {
            if (event.amount() == null || event.amount() <= 0) {
                return Optional.empty();
            }
            values.put("환불금액", NumberFormat.getNumberInstance(Locale.KOREA).format(event.amount()));
        } else {
            Delivery delivery = deliveryRepository.findByOrderId(order.getId()).orElse(null);
            if (delivery == null || delivery.getTrackingNo() == null || delivery.getCarrier() == null) {
                return Optional.empty();
            }
            values.put("택배사", delivery.getCarrier());
            values.put("송장번호", delivery.getTrackingNo());
        }

        return Optional.of(new AlimtalkMessage(phone, template, templateCode, values, template.render(values),
                siteUrl + AlimtalkTemplate.BUTTON_PATH));
    }

    static String productSummary(List<OrderItem> items) {
        if (items.isEmpty()) {
            return "주문 상품";
        }
        String first = items.get(0).getProductNameSnapshot();
        return items.size() == 1 ? first : first + " 외 " + (items.size() - 1) + "건";
    }

    private String decrypt(String encrypted) {
        try {
            return phoneCipher.decrypt(encrypted);
        } catch (RuntimeException e) {
            return null;
        }
    }
    private static String digits(String s) {
        return s == null ? "" : s.replaceAll("\\D", "");
    }
}