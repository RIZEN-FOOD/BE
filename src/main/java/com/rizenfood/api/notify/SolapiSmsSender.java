package com.rizenfood.api.notify;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/**
 * 솔라피 문자 발송 (2026-10-16). 키·발신번호는 알림톡과 같은 값(app.alimtalk.solapi)을 쓴다.
 * 종류(type)를 비워 보내면 솔라피가 길이에 따라 단문(SMS)·장문(LMS)을 고른다.
 * 서명·응답 확인은 SolapiAlimtalkSender 와 같다.
 */
@Component
class SolapiSmsSender implements SmsSender {

    private static final String URL = "https://api.solapi.com/messages/v4/send-many/detail";
    private static final String SALT_CHARS = "1234567890abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final SolapiProperties properties;
    private final RestClient http;
    private final SecureRandom random = new SecureRandom();

    SolapiSmsSender(SolapiProperties properties) {
        this.properties = properties;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(Duration.ofSeconds(10));
        this.http = RestClient.builder().requestFactory(factory).build();
    }

    @Override
    public void send(String to, String text) {
        if (properties.apiKey().isEmpty() || properties.apiSecret().isEmpty() || properties.from().isEmpty()) {
            throw new AlimtalkSender.AlimtalkException("솔라피 설정(API Key·Secret·발신번호)이 비어 있다");
        }
        String body;
        try {
            body = http.post()
                    .uri(URL)
                    .header("Authorization", SolapiAlimtalkSender.authorization(properties.apiKey(), properties.apiSecret(),
                            OffsetDateTime.now(KST).truncatedTo(ChronoUnit.SECONDS)
                                    .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
                            salt()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(requestBody(to, properties.from(), text))
                    .retrieve()
                    .body(String.class);
        } catch (RestClientResponseException e) {
            throw new AlimtalkSender.AlimtalkException("솔라피 문자 거절 HTTP " + e.getStatusCode().value());
        } catch (RestClientException e) {
            throw new AlimtalkSender.AlimtalkException("솔라피에 연결하지 못했다", e);
        }
        SolapiAlimtalkSender.checkAccepted(body);
    }

    static Map<String, Object> requestBody(String to, String from, String text) {
        Map<String, Object> one = new LinkedHashMap<>();
        one.put("to", to);
        one.put("from", from);
        one.put("text", text);
        return Map.of("messages", List.of(one));
    }

    private String salt() {
        StringBuilder sb = new StringBuilder(32);
        for (int i = 0; i < 32; i++) {
            sb.append(SALT_CHARS.charAt(random.nextInt(SALT_CHARS.length())));
        }
        return sb.toString();
    }
}
