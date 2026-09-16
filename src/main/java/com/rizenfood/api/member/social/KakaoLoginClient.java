package com.rizenfood.api.member.social;

import java.time.Duration;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponentsBuilder;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * 카카오 로그인.
 *
 * 콘솔: https://developers.kakao.com → 내 애플리케이션 → 앱 키의 "REST API 키"가 client id 다.
 * 보안 → Client Secret 을 켰으면 그 값도 넣는다(켜는 것을 권장).
 *
 * ★ 이메일은 카카오 "비즈 앱"으로 전환해야 필수 동의로 받을 수 있다. 선택 동의로 두면
 *   손님이 거부할 수 있고, 그 경우 이메일 없이 가입된다(SocialLoginService 가 처리).
 */
@Component
public class KakaoLoginClient implements SocialProviderClient {

    static final String PROVIDER = "kakao";
    private static final String AUTHORIZE = "https://kauth.kakao.com/oauth/authorize";
    private static final String TOKEN = "https://kauth.kakao.com/oauth/token";
    private static final String PROFILE = "https://kapi.kakao.com/v2/user/me";

    private final SocialProperties.Client config;
    private final RestClient http;

    public KakaoLoginClient(SocialProperties properties) {
        this.config = properties.kakao();
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(Duration.ofSeconds(8));
        this.http = RestClient.builder().requestFactory(factory).build();
    }

    @Override
    public String provider() {
        return PROVIDER;
    }

    @Override
    public boolean enabled() {
        return config.enabled();
    }

    @Override
    public String authorizeUrl(String state, String redirectUri) {
        return UriComponentsBuilder.fromUriString(AUTHORIZE)
                .queryParam("response_type", "code")
                .queryParam("client_id", config.clientId())
                .queryParam("redirect_uri", redirectUri)
                .queryParam("state", state)
                .encode()
                .build()
                .toUriString();
    }

    @Override
    public SocialProfile fetchProfile(String code, String state, String redirectUri) {
        try {
            MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
            form.add("grant_type", "authorization_code");
            form.add("client_id", config.clientId());
            form.add("redirect_uri", redirectUri);
            form.add("code", code);
            if (config.hasSecret()) {
                form.add("client_secret", config.clientSecret());
            }

            JsonNode token = http.post().uri(TOKEN)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(JsonNode.class);
            String accessToken = token == null ? "" : token.path("access_token").asText("");
            if (accessToken.isBlank()) {
                throw new SocialLoginException("카카오 토큰 응답에 access_token 이 없다");
            }

            JsonNode me = http.get().uri(PROFILE)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                    .retrieve()
                    .body(JsonNode.class);
            if (me == null || me.path("id").asText("").isBlank()) {
                throw new SocialLoginException("카카오 회원 정보 응답에 id 가 없다");
            }

            JsonNode account = me.path("kakao_account");
            String email = text(account, "email");
            // 카카오는 "유효한 이메일"과 "인증된 이메일"을 따로 알려준다. 둘 다여야 믿는다.
            boolean verified = account.path("is_email_valid").asBoolean(false)
                    && account.path("is_email_verified").asBoolean(false);
            String name = firstNonBlank(
                    text(account, "name"),
                    text(account.path("profile"), "nickname"),
                    text(me.path("properties"), "nickname"));

            return new SocialProfile(PROVIDER, me.path("id").asText(), email, verified, name);
        } catch (RestClientException e) {
            throw new SocialLoginException("카카오 통신 실패: " + e.getMessage(), e);
        }
    }

    static String text(JsonNode node, String field) {
        JsonNode v = node.path(field);
        return v.isMissingNode() || v.isNull() ? null : v.asText();
    }

    static String firstNonBlank(String... values) {
        for (String v : values) {
            if (v != null && !v.isBlank()) {
                return v.trim();
            }
        }
        return null;
    }
}
