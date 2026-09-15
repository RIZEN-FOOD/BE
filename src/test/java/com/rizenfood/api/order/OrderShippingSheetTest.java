package com.rizenfood.api.order;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 출고용 엑셀의 연락처 표기. 택배 송장에 그대로 찍히므로 하이픈 형태로 맞춘다. */
class OrderShippingSheetTest {

    @Test
    @DisplayName("휴대폰·지역번호·서울 번호를 하이픈 형태로")
    void formatsPhones() {
        assertThat(OrderShippingSheet.formatPhone("01012345678")).isEqualTo("010-1234-5678");
        assertThat(OrderShippingSheet.formatPhone("0317654321")).isEqualTo("031-765-4321");
        assertThat(OrderShippingSheet.formatPhone("0212345678")).isEqualTo("02-1234-5678");
        assertThat(OrderShippingSheet.formatPhone("021234567")).isEqualTo("02-123-4567");
        assertThat(OrderShippingSheet.formatPhone("07080989542")).isEqualTo("070-8098-9542");
    }

    @Test
    @DisplayName("모르는 형태는 그대로, null 은 null")
    void leavesUnknown() {
        assertThat(OrderShippingSheet.formatPhone("1588-0000")).isEqualTo("1588-0000");
        assertThat(OrderShippingSheet.formatPhone(null)).isNull();
    }

    @Test
    @DisplayName("상품 한 줄 = 엑셀 한 줄, 배송비·결제금액은 주문 첫 줄에만")
    void buildsOneRowPerItem() throws Exception {
        Order o = new Order();
        o.setOrderNo("R20260915-ABCD1234");
        o.setOrdererName("홍길동");
        o.setOrdererPhoneEncrypted("enc-orderer");
        o.setReceiverName("김받는");
        o.setReceiverPhoneEncrypted("enc-receiver");
        o.setZipcode("06234");
        o.setAddr1("서울 강남구 테헤란로 1");
        o.setAddr2("101호");
        o.setDeliveryMemo("=문 앞에 놔주세요");
        o.setShippingFee(0);
        o.setTotalAmount(51_600);
        org.springframework.test.util.ReflectionTestUtils.setField(o, "status", "PAID");
        org.springframework.test.util.ReflectionTestUtils.setField(o, "orderedAt",
                java.time.Instant.parse("2026-09-15T04:30:00Z"));
        o.addItem(new OrderItem(1L, null, "크림오브라이스", null, null, 12_900, 3));
        o.addItem(new OrderItem(1L, 2L, "크림오브라이스", "1kg", null, 12_900, 1));

        java.util.Map<String, String> phones = java.util.Map.of(
                "enc-orderer", "01011112222", "enc-receiver", "01033334444");
        byte[] file = OrderShippingSheet.build(java.util.List.of(o), phones::get);

        // 엑셀로 열어 확인할 수 있게 남겨둔다 (build/tmp — 커밋되지 않는다).
        java.nio.file.Path out = java.nio.file.Path.of("build", "tmp", "shipping-sheet-sample.xlsx");
        java.nio.file.Files.createDirectories(out.getParent());
        java.nio.file.Files.write(out, file);

        String sheet;
        try (java.util.zip.ZipInputStream zip = new java.util.zip.ZipInputStream(
                new java.io.ByteArrayInputStream(file), java.nio.charset.StandardCharsets.UTF_8)) {
            java.util.zip.ZipEntry e;
            String found = null;
            while ((e = zip.getNextEntry()) != null) {
                if (e.getName().equals("xl/worksheets/sheet1.xml")) {
                    found = new String(zip.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
                }
            }
            sheet = found;
        }
        assertThat(sheet).contains("<row r=\"3\">").doesNotContain("<row r=\"4\">");
        assertThat(sheet).contains(">06234<", ">010-3333-4444<", ">2026-09-15 13:30<", ">결제 완료<");
        assertThat(sheet).contains("<c r=\"P2\"><v>51600</v></c>").doesNotContain("P3");
        assertThat(sheet).contains(">=문 앞에 놔주세요<").doesNotContain("<f>");
    }
}
