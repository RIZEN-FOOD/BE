package com.rizenfood.api.notify;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@Component
@ConditionalOnProperty(name = "app.alimtalk.provider", havingValue = "solapi")
class SolapiAlimtalkSender implements AlimtalkSender {

    private static final String URL = "https://api.solapi.com/messages/v4/send-many/detail";
    private static final String SALT_CHARS = "1234567890abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final SolapiProperties properties;
    private final RestClient http;
    private final SecureRandom random = new SecureRandom();

    SolapiAlimtalkSender(SolapiProperties properties) {
        this.properties = properties;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(Duration.ofSeconds(10));
        this.http = RestClient.builder().requestFactory(factory).build();
    }

    @Override
    public String provider() {
        return "solapi";
    }

    @Override
    public void send(AlimtalkMessage message) {
        if (!properties.ready()) {
            throw new AlimtalkException("솔라피 설정(API Key·Secret·pfId·발신번호)이 비어 있다");
        }
        String body;
        try {
            body = http.post()
                    .uri(URL)
                    .header("Authorization", authorization(properties.apiKey(), properties.apiSecret(),
                            OffsetDateTime.now(KST).truncatedTo(ChronoUnit.SECONDS)
                                    .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
                            salt()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(requestBody(message, properties.pfId(), properties.from()))
                    .retrieve()
                    .body(String.class);
        } catch (RestClientResponseException e) {
            throw new AlimtalkException("솔라피 거절 HTTP " + e.getStatusCode().value() + " " + errorCode(e));
        } catch (RestClientException e) {
            throw new AlimtalkException("솔라피에 연결하지 못했다", e);
        }
        checkAccepted(body);
    }
    // ── 요청 ─────────────────────────────────────────────────

    static String authorization(String apiKey, String apiSecret, String date, String salt) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(apiSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            String signature = HexFormat.of().formatHex(mac.doFinal((date + salt).getBytes(StandardCharsets.UTF_8)));
            return "HMAC-SHA256 apiKey=" + apiKey + ", date=" + date + ", salt=" + salt + ", signature=" + signature;
        } catch (Exception e) {
            throw new AlimtalkException("솔라피 서명을 만들지 못했다", e);
        }
    }

    static Map<String, Object> requestBody(AlimtalkMessage message, String pfId, String from) {
        Map<String, String> variables = new LinkedHashMap<>();
        message.variables().forEach((k, v) -> variables.put("#{" + k + "}", v));
        Map<String, Object> kakaoOptions = new LinkedHashMap<>();
        kakaoOptions.put("pfId", pfId);
        kakaoOptions.put("templateId", message.templateCode());
        kakaoOptions.put("variables", variables);
        kakaoOptions.put("disableSms", true);
        Map<String, Object> one = new LinkedHashMap<>();
        one.put("to", message.to());
        one.put("from", from);
        one.put("type", "ATA");
        one.put("kakaoOptions", kakaoOptions);

        return Map.of("messages", List.of(one));
    }
    private String salt() {
        StringBuilder sb = new StringBuilder(32);
        for (int i = 0; i < 32; i++) {
            sb.append(SALT_CHARS.charAt(random.nextInt(SALT_CHARS.length())));
        }
        return sb.toString();
    }

    // ── 응답 ─────────────────────────────────────────────────

    static void checkAccepted(String body) {
        JsonNode root;
        try {
            root = MAPPER.readTree(body == null ? "{}" : body);
        } catch (Exception e) {
            throw new AlimtalkException("솔라피 응답을 읽지 못했다");
        }
        JsonNode failed = root.path("failedMessageList");
        if (failed.isArray() && !failed.isEmpty()) {
            JsonNode f = failed.get(0);
            throw new AlimtalkException("솔라피 접수 실패 " + f.path("statusCode").asText("")
                    + " " + f.path("statusMessage").asText(""));
        }
    }
    private static String errorCode(RestClientResponseException e) {
        try {
            return MAPPER.readTree(e.getResponseBodyAsString()).path("errorCode").asText("");
        } catch (Exception ignored) {
            return "";
        }
    }
}