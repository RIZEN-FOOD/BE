package com.rizenfood.api.naverpay;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.xml.parsers.DocumentBuilderFactory;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;

/**
 * 네이버페이 XML (가이드 2.1 표 3-2·3-8·3-14).
 *
 * 네이버가 읽을 수 있는 올바른 XML 인지, 필수 항목이 다 있는지, 상품명에 특수문자가 있어도
 * 깨지지 않는지를 실제 XML 파서로 다시 읽어 확인한다.
 */
class NaverPayXmlTest {

    private static final NaverPayXml.Shipping CONDITIONAL =
            new NaverPayXml.Shipping("1", "CONDITIONAL_FREE", "PREPAYED", 3_500, 50_000, true);

    private static NaverPayXml.ProductInfo product(String name, Integer returnFee, Integer exchangeFee) {
        return new NaverPayXml.ProductInfo(7L, name, 9_900, "TAX",
                "https://www.rizenfoods.com/products/cream-of-rice",
                "https://www.rizenfoods.com/uploads/p_large.webp",
                "ON_SALE", 33, returnFee, exchangeFee, CONDITIONAL);
    }

    private static Document parse(String xml) throws Exception {
        return DocumentBuilderFactory.newInstance().newDocumentBuilder()
                .parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
    }

    private static String text(Document d, String tag) {
        var nodes = d.getElementsByTagName(tag);
        return nodes.getLength() == 0 ? null : nodes.item(0).getTextContent();
    }

    @Test
    @DisplayName("주문 등록 XML — 필수 항목과 조건부 무료 배송비·도서산간 API 사용이 들어간다")
    void orderXmlHasRequiredFields() throws Exception {
        String xml = NaverPayXml.order("np_test", "CERTI-1", product("크림오브라이스", null, null), 2,
                "https://www.rizenfoods.com/products/cream-of-rice", NaverPayXml.Inflow.NONE);
        Document d = parse(xml);

        assertThat(text(d, "merchantId")).isEqualTo("np_test");
        assertThat(text(d, "certiKey")).isEqualTo("CERTI-1");
        assertThat(text(d, "id")).isEqualTo("7");
        assertThat(text(d, "ecMallProductId")).isEqualTo("7");
        assertThat(text(d, "basePrice")).isEqualTo("9900");
        assertThat(text(d, "taxType")).isEqualTo("TAX");
        assertThat(text(d, "quantity")).isEqualTo("2");
        assertThat(text(d, "backUrl")).isEqualTo("https://www.rizenfoods.com/products/cream-of-rice");
        assertThat(text(d, "feeType")).isEqualTo("CONDITIONAL_FREE");
        assertThat(text(d, "feePayType")).isEqualTo("PREPAYED");
        assertThat(text(d, "feePrice")).isEqualTo("3500");
        assertThat(d.getElementsByTagName("conditionalFree").item(0).getTextContent()).isEqualTo("50000");
        assertThat(text(d, "apiSupport")).isEqualTo("true");
        // 상품정보 응답에만 쓰는 항목은 주문 등록에 넣지 않는다
        assertThat(text(d, "status")).isNull();
        assertThat(text(d, "stockQuantity")).isNull();
        assertThat(d.getElementsByTagName("interface").getLength()).as("유입 경로가 없으면 넣지 않는다").isZero();
    }

    @Test
    @DisplayName("유입 경로 쿠키가 있으면 interface 에 담는다")
    void orderXmlCarriesInflow() throws Exception {
        String xml = NaverPayXml.order("np_test", "CERTI-1", product("상품", null, null), 1, "https://x/p",
                new NaverPayXml.Inflow("NA-CO-VALUE", null, "NVADID-1"));
        Document d = parse(xml);

        assertThat(text(d, "naverInflowCode")).isEqualTo("NA-CO-VALUE");
        assertThat(text(d, "saClickId")).isEqualTo("NVADID-1");
        assertThat(text(d, "cpaInflowCode")).as("빈 값은 넣지 않는다").isNull();
    }

    @Test
    @DisplayName("상품명에 & < > \" 가 있어도 XML 이 깨지지 않고 그대로 읽힌다")
    void escapesSpecialCharacters() throws Exception {
        String tricky = "쌀 & 귀리 <특가> \"한정\" ]]>";
        String xml = NaverPayXml.order("np_test", "CERTI-1", product(tricky, null, null), 1, "https://x/p",
                NaverPayXml.Inflow.NONE);

        assertThat(text(parse(xml), "name")).isEqualTo(tricky);
    }

    @Test
    @DisplayName("상품정보 XML — 판매 상태·재고를 넣고, 반품·교환비는 정했을 때만 넣는다")
    void productsXml() throws Exception {
        Document noFees = parse(NaverPayXml.products(List.of(product("상품", null, null))));
        assertThat(text(noFees, "status")).isEqualTo("ON_SALE");
        assertThat(text(noFees, "stockQuantity")).isEqualTo("33");
        assertThat(text(noFees, "optionSupport")).isEqualTo("false");
        assertThat(text(noFees, "returnShippingFee")).as("비우면 페이센터 기본 정책을 쓴다").isNull();
        assertThat(text(noFees, "exchangeShippingFee")).isNull();

        Document withFees = parse(NaverPayXml.products(List.of(product("상품", 3_500, 7_000))));
        assertThat(text(withFees, "returnShippingFee")).isEqualTo("3500");
        assertThat(text(withFees, "exchangeShippingFee")).isEqualTo("7000");

        Document outOfRange = parse(NaverPayXml.products(List.of(product("상품", 0, 300_000))));
        assertThat(text(outOfRange, "returnShippingFee")).as("1~200,000 밖이면 넣지 않는다").isNull();
        assertThat(text(outOfRange, "exchangeShippingFee")).isNull();
    }

    @Test
    @DisplayName("도서산간비 XML — 상품마다 추가 배송비")
    void additionalFeesXml() throws Exception {
        Map<String, Integer> fees = new LinkedHashMap<>();
        fees.put("7", 3_000);
        fees.put("8", 3_000);
        Document d = parse(NaverPayXml.additionalFees(fees));

        assertThat(d.getElementsByTagName("additionalFee").getLength()).isEqualTo(2);
        assertThat(d.getElementsByTagName("id").item(1).getTextContent()).isEqualTo("8");
        assertThat(d.getElementsByTagName("surprice").item(0).getTextContent()).isEqualTo("3000");
    }
}
