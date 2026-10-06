package com.rizenfood.api.naverpay;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * 실제 네이버페이 서버로 보내는 구현.
 *
 * 가이드 예제(PHP)와 같은 형식: POST, Content-Type application/xml; charset=utf-8, 본문은 XML.
 * 손님이 버튼을 누르고 기다리는 중이라 오래 매달리지 않는다(연결 5초, 응답 10초 — 가이드 예제도 10초).
 */
@Component
class HttpNaverPayClient implements NaverPayClient {

    private static final MediaType XML_UTF8 = new MediaType("application", "xml", StandardCharsets.UTF_8);

    private final RestClient http;

    HttpNaverPayClient() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(Duration.ofSeconds(10));
        this.http = RestClient.builder().requestFactory(factory).build();
    }

    @Override
    public String post(String url, String xml) {
        try {
            String body = http.post()
                    .uri(url)
                    .contentType(XML_UTF8)
                    .body(xml.getBytes(StandardCharsets.UTF_8))
                    .retrieve()
                    .body(String.class);
            return body == null ? "" : body;
        } catch (RestClientException e) {
            throw new NaverPayException("네이버페이 서버에 연결하지 못했다", e);
        }
    }
}
