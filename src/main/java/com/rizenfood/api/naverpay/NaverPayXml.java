package com.rizenfood.api.naverpay;

import java.io.StringWriter;
import java.util.List;
import java.util.Map;

import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;

/**
 * 네이버페이와 주고받는 XML 을 만든다 (가이드 2.1 표 3-2·3-4·3-8·3-14).
 *
 * ★ 문자열을 이어 붙이지 않고 XML 작성기를 쓴다. 상품명에 &·<·" 가 들어가도 깨지지 않게
 *   글자 이스케이프를 작성기에 맡긴다(가이드 예제는 CDATA 를 쓰지만 결과는 같다).
 * ★ 화면이 보낸 값을 넣지 않는다. 가격·재고·배송비는 호출하는 쪽이 DB 에서 읽은 값이다.
 */
final class NaverPayXml {

    private static final XMLOutputFactory FACTORY = XMLOutputFactory.newInstance();

    private NaverPayXml() {
    }

    /**
     * 네이버페이에 보내는 상품 한 개의 정보.
     *
     * @param status              ON_SALE | SOLD_OUT | NOT_SALE (상품정보 응답에만 쓴다)
     * @param stockQuantity       재고 (상품정보 응답에만 쓴다)
     * @param returnShippingFee   편도 반품 배송비. null 이면 넣지 않는다 → 페이센터 기본 반품 정책
     * @param exchangeShippingFee 왕복 교환 배송비. null 이면 넣지 않는다
     */
    record ProductInfo(
            long id,
            String name,
            int basePrice,
            String taxType,
            String infoUrl,
            String imageUrl,
            String status,
            int stockQuantity,
            Integer returnShippingFee,
            Integer exchangeShippingFee,
            Shipping shipping) {
    }

    /**
     * 배송비 정책 (표 3-4).
     *
     * @param groupId                  묶음 그룹. 같은 값이면 한 번만 청구된다
     * @param feeType                  FREE | CHARGE | CONDITIONAL_FREE
     * @param feePayType               FREE | PREPAYED
     * @param conditionalFreeBasePrice 조건부 무료 기준 금액. CONDITIONAL_FREE 일 때만
     * @param surchargeApi             도서산간비를 우리 API 로 알려주는가
     */
    record Shipping(
            String groupId,
            String feeType,
            String feePayType,
            int feePrice,
            Integer conditionalFreeBasePrice,
            boolean surchargeApi) {
    }

    /** 유입 경로 값 (표 3-2 interface/*). 네이버 공통 스크립트가 우리 도메인에 심은 쿠키에서 읽는다. */
    record Inflow(String naverInflowCode, String cpaInflowCode, String saClickId) {
        static final Inflow NONE = new Inflow(null, null, null);
    }

    /** 주문 정보 등록 XML (표 3-2). 단일 상품 1종, 옵션·추가상품 없음. */
    static String order(String merchantId, String certiKey, ProductInfo p, int quantity, String backUrl,
                        Inflow inflow) {
        return write(w -> {
            w.writeStartElement("order");
            text(w, "merchantId", merchantId);
            text(w, "certiKey", certiKey);

            w.writeStartElement("product");
            writeProductBasics(w, p);
            w.writeStartElement("single");
            text(w, "quantity", String.valueOf(quantity));
            w.writeEndElement();
            writeShipping(w, p.shipping());
            w.writeEndElement(); // product

            text(w, "backUrl", backUrl);

            Inflow in = inflow == null ? Inflow.NONE : inflow;
            if (hasAny(in.naverInflowCode(), in.cpaInflowCode(), in.saClickId())) {
                w.writeStartElement("interface");
                optional(w, "cpaInflowCode", in.cpaInflowCode());
                optional(w, "naverInflowCode", in.naverInflowCode());
                optional(w, "saClickId", in.saClickId());
                w.writeEndElement();
            }
            w.writeEndElement(); // order
        });
    }

    /** 상품 정보 응답 XML (표 3-8). 요청받은 상품 번호마다 하나씩. */
    static String products(List<ProductInfo> list) {
        return write(w -> {
            w.writeStartElement("products");
            for (ProductInfo p : list) {
                w.writeStartElement("product");
                writeProductBasics(w, p);
                text(w, "status", p.status());
                text(w, "stockQuantity", String.valueOf(Math.max(0, p.stockQuantity())));
                text(w, "optionSupport", "false");
                text(w, "supplementSupport", "false");
                // 1~200,000 범위 밖이면 네이버가 무시하므로 아예 넣지 않는다(비우면 페이센터 기본 정책).
                if (inFeeRange(p.returnShippingFee())) {
                    text(w, "returnShippingFee", String.valueOf(p.returnShippingFee()));
                }
                if (inFeeRange(p.exchangeShippingFee())) {
                    text(w, "exchangeShippingFee", String.valueOf(p.exchangeShippingFee()));
                }
                writeShipping(w, p.shipping());
                w.writeEndElement();
            }
            w.writeEndElement();
        });
    }

    /** 도서산간 추가 배송비 응답 XML (표 3-14). 상품 번호 → 추가 배송비. */
    static String additionalFees(Map<String, Integer> feeByProductId) {
        return write(w -> {
            w.writeStartElement("additionalFees");
            for (Map.Entry<String, Integer> e : feeByProductId.entrySet()) {
                w.writeStartElement("additionalFee");
                text(w, "id", e.getKey());
                text(w, "surprice", String.valueOf(Math.max(0, e.getValue())));
                w.writeEndElement();
            }
            w.writeEndElement();
        });
    }

    private static void writeProductBasics(XMLStreamWriter w, ProductInfo p) throws XMLStreamException {
        String id = String.valueOf(p.id());
        text(w, "id", id);
        // 네이버쇼핑 EP 의 mall_pid 와 같아야 한다. EP 를 붙이면 같은 상품 번호를 쓴다.
        text(w, "ecMallProductId", id);
        text(w, "name", p.name());
        text(w, "basePrice", String.valueOf(p.basePrice()));
        text(w, "taxType", p.taxType());
        text(w, "infoUrl", p.infoUrl());
        text(w, "imageUrl", p.imageUrl());
    }

    private static void writeShipping(XMLStreamWriter w, Shipping s) throws XMLStreamException {
        w.writeStartElement("shippingPolicy");
        text(w, "groupId", s.groupId());
        text(w, "method", "DELIVERY");
        text(w, "feeType", s.feeType());
        text(w, "feePayType", s.feePayType());
        text(w, "feePrice", String.valueOf(s.feePrice()));
        if ("CONDITIONAL_FREE".equals(s.feeType()) && s.conditionalFreeBasePrice() != null) {
            w.writeStartElement("conditionalFree");
            text(w, "basePrice", String.valueOf(s.conditionalFreeBasePrice()));
            w.writeEndElement();
        }
        if (s.surchargeApi()) {
            // 도서산간비는 우리 /api/naverpay/additional-fee 가 우편번호로 판정해 알려준다(표 3-13).
            w.writeStartElement("surchargeByArea");
            text(w, "apiSupport", "true");
            w.writeEndElement();
        }
        w.writeEndElement();
    }

    private static boolean inFeeRange(Integer fee) {
        return fee != null && fee >= 1 && fee <= 200_000;
    }

    private static boolean hasAny(String... values) {
        for (String v : values) {
            if (v != null && !v.isBlank()) {
                return true;
            }
        }
        return false;
    }

    private static void optional(XMLStreamWriter w, String name, String value) throws XMLStreamException {
        if (value != null && !value.isBlank()) {
            text(w, name, value);
        }
    }

    private static void text(XMLStreamWriter w, String name, String value) throws XMLStreamException {
        w.writeStartElement(name);
        w.writeCharacters(value == null ? "" : value);
        w.writeEndElement();
    }

    @FunctionalInterface
    private interface Body {
        void accept(XMLStreamWriter w) throws XMLStreamException;
    }

    private static String write(Body body) {
        StringWriter out = new StringWriter();
        try {
            XMLStreamWriter w = FACTORY.createXMLStreamWriter(out);
            w.writeStartDocument("utf-8", "1.0");
            body.accept(w);
            w.writeEndDocument();
            w.flush();
            w.close();
        } catch (XMLStreamException e) {
            throw new IllegalStateException("네이버페이 XML 을 만들지 못했다", e);
        }
        return out.toString();
    }
}
