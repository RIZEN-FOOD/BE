package com.rizenfood.api.order;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;

import com.rizenfood.api.common.SimpleXlsxWriter;

/**
 * 출고 대행사(3PL)에 넘기는 주문 엑셀.
 *
 * 상품 한 줄 = 엑셀 한 줄. 한 주문에 상품이 여러 개면 주문번호·배송지가 줄마다 반복되고,
 * 배송비·결제금액은 합계가 두 번 잡히지 않게 그 주문의 첫 줄에만 적는다.
 *
 * 대행사 전용 양식을 받으면 아래 HEADERS 와 row() 의 칸 순서만 맞추면 된다.
 */
final class OrderShippingSheet {

    private OrderShippingSheet() {
    }

    /**
     * 맨 뒤 두 칸(택배사·송장번호)은 <b>빈 칸으로 내보낸다.</b>
     * 출고 대행사가 그 자리에 송장을 채워 그대로 돌려주면 관리자에서 한 번에 올릴 수 있다
     * ({@link OrderTrackingSheet} 가 같은 칸 이름을 찾아 읽는다).
     */
    static final List<String> HEADERS = List.of(
            "주문번호", "주문일시", "주문상태",
            "받는 분", "받는 분 연락처", "우편번호", "주소", "상세주소", "배송메모",
            "상품명", "옵션", "수량",
            "주문자", "주문자 연락처", "배송비", "결제금액",
            "택배사", "송장번호");

    private static final int[] WIDTHS = {22, 17, 11, 10, 15, 8, 44, 24, 24, 26, 14, 6, 10, 15, 9, 11, 12, 18};

    private static final DateTimeFormatter KST =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.of("Asia/Seoul"));

    /** 주문 상태를 대표가 읽을 수 있는 말로. 엑셀과 오류 안내에서 같이 쓴다. */
    static final Map<String, String> STATUS_LABEL = Map.of(
            "PENDING", "결제 대기", "PAID", "결제 완료", "PREPARING", "상품 준비중",
            "SHIPPED", "배송중", "DELIVERED", "배송 완료", "CANCELLED", "취소됨", "REFUNDED", "환불됨");

    /** @param decryptPhone 암호화된 연락처 → 숫자만 남은 번호 (실패하면 null) */
    static byte[] build(List<Order> orders, UnaryOperator<String> decryptPhone) {
        List<List<Object>> rows = new ArrayList<>();
        for (Order o : orders) {
            boolean first = true;
            for (OrderItem it : o.getItems()) {
                rows.add(row(o, it, first, decryptPhone));
                first = false;
            }
        }
        return SimpleXlsxWriter.write("주문", HEADERS, rows, WIDTHS);
    }

    private static List<Object> row(Order o, OrderItem it, boolean first, UnaryOperator<String> decryptPhone) {
        return Arrays.asList(
                o.getOrderNo(),
                o.getOrderedAt() != null ? KST.format(o.getOrderedAt()) : null,
                STATUS_LABEL.getOrDefault(o.getStatus(), o.getStatus()),
                o.getReceiverName(),
                formatPhone(decryptPhone.apply(o.getReceiverPhoneEncrypted())),
                o.getZipcode(),
                o.getAddr1(),
                o.getAddr2(),
                o.getDeliveryMemo(),
                it.getProductNameSnapshot(),
                it.getOptionNameSnapshot(),
                it.getQuantity(),
                o.getOrdererName(),
                formatPhone(decryptPhone.apply(o.getOrdererPhoneEncrypted())),
                first ? o.getShippingFee() : null,
                first ? o.getTotalAmount() : null,
                null,   // 택배사 — 대행사가 채운다
                null);  // 송장번호 — 대행사가 채운다
    }

    /** 숫자만 있는 번호를 010-1234-5678 형태로. 모르는 형태면 그대로 둔다. */
    static String formatPhone(String raw) {
        if (raw == null) {
            return null;
        }
        String d = raw.replaceAll("\\D", "");
        if (d.startsWith("02")) {
            if (d.length() == 9) return d.substring(0, 2) + "-" + d.substring(2, 5) + "-" + d.substring(5);
            if (d.length() == 10) return d.substring(0, 2) + "-" + d.substring(2, 6) + "-" + d.substring(6);
        } else {
            if (d.length() == 10) return d.substring(0, 3) + "-" + d.substring(3, 6) + "-" + d.substring(6);
            if (d.length() == 11) return d.substring(0, 3) + "-" + d.substring(3, 7) + "-" + d.substring(7);
        }
        return raw;
    }
}
