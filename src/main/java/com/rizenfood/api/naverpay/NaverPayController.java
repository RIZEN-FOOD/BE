package com.rizenfood.api.naverpay;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 네이버페이 주문형 주소 (2026-10-06). 로그인 없이 열려 있다(손님·네이버 서버가 부른다).
 *
 *   GET  /api/naverpay/config          화면이 버튼을 띄울지 정한다(꺼져 있으면 enabled=false)
 *   POST /api/naverpay/orders          [N Pay 구매] — 주문 정보 등록 후 {key, merchantNo}
 *   GET  /api/naverpay/product-info    네이버 → 상품 정보 XML (페이센터에 이 주소를 등록한다)
 *   GET  /api/naverpay/additional-fee  네이버 → 도서산간비 XML (페이센터에 이 주소를 등록한다)
 *
 * ★ 꺼져 있거나 키가 비면 config 말고는 전부 404 다.
 * ★ product-info·additional-fee 는 공개 정보(가격·재고·배송비)만 내보낸다.
 */
@RestController
@RequestMapping("/api/naverpay")
public class NaverPayController {

    private static final MediaType XML_UTF8 = new MediaType("application", "xml", StandardCharsets.UTF_8);

    private final NaverPayService service;

    public NaverPayController(NaverPayService service) {
        this.service = service;
    }

    @GetMapping("/config")
    public NaverPayService.PublicConfig config() {
        return service.publicConfig();
    }

    /** 화면은 «무엇을 몇 개»만 보낸다. 가격은 서버가 DB 에서 읽는다. */
    public record OrderRequest(@NotNull(message = "상품을 선택해 주세요.") Long productId, int quantity) {
    }

    @PostMapping("/orders")
    public NaverPayRegisterResult order(@Valid @RequestBody OrderRequest req, HttpServletRequest request) {
        // 네이버 공통 유입 경로 스크립트가 우리 도메인에 심은 쿠키(가이드 4장·표 3-2).
        NaverPayXml.Inflow inflow = new NaverPayXml.Inflow(
                cookie(request, "NA_CO"), cookie(request, "CPAValidator"), cookie(request, "NVADID"));
        return service.register(req.productId(), req.quantity(), inflow);
    }

    @GetMapping("/product-info")
    public ResponseEntity<String> productInfo(HttpServletRequest request) {
        return ResponseEntity.ok().contentType(XML_UTF8).body(service.productInfoXml(request.getQueryString()));
    }

    @GetMapping("/additional-fee")
    public ResponseEntity<String> additionalFee(HttpServletRequest request) {
        return ResponseEntity.ok().contentType(XML_UTF8).body(service.additionalFeeXml(request.getQueryString()));
    }

    /** 네이버 쪽 거절·통신 실패. 사유는 로그에 남기고 손님에게는 다시 시도하라고만 한다. */
    @ExceptionHandler(NaverPayException.class)
    public ResponseEntity<Map<String, String>> handleNaverPay(NaverPayException e) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of(
                "error", "NAVERPAY_UNAVAILABLE",
                "message", "네이버페이 주문을 시작하지 못했습니다. 잠시 후 다시 시도하거나 사이트에서 바로 결제해 주세요."));
    }

    private static String cookie(HttpServletRequest request, String name) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie c : cookies) {
            if (name.equals(c.getName())) {
                String v = c.getValue();
                // 네이버로 그대로 넘어가는 값이라 길이를 막는다(필드당 최대 300자).
                return v == null || v.isBlank() ? null : (v.length() > 300 ? v.substring(0, 300) : v);
            }
        }
        return null;
    }
}
