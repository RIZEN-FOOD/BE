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
 * 네이버 로그인.
 *
 * 콘솔: https://developers.naver.com → Application → 내 애플리케이션 → Client ID / Client Secret.
 * 네이버는 Client Secret 이 필수다.
 *
 * ★ 네이버 계정의 이메일은 네이버가 가입 시 확인한 연락처 이메일이라 확인된 것으로 본다.
 *   손님이 제공 동의를 거부하면 이메일 없이 온다.
 */
@Component
public class NaverLoginClient implements SocialProviderClient {

    static final String PROVIDER = "naver";
    private static final String AUTHORIZE = "https://nid.naver.com/oauth2.0/authorize";
    private static final String TOKEN = "https://nid.naver.com/oauth2.0/token";
    private static final String PROFILE = "https://openapi.naver.com/v1/nid/me";

    private final SocialProperties.Client config;
    private final RestClient http;

    public NaverLoginClient(SocialProperties properties) {
        this.config = properties.naver();
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
        // 네이버는 Secret 없이는 토큰을 받을 수 없다.
        return config.enabled() && config.hasSecret();
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
            form.add("client_secret", config.clientSecret());
            form.add("code", code);
            form.add("state", state);

            JsonNode token = http.post().uri(TOKEN)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(JsonNode.class);
            // 네이버는 실패해도 200 으로 error 필드를 담아 보낸다.
            if (token == null || !token.path("error").asText("").isBlank()) {
                throw new SocialLoginException("네이버 토큰 발급 실패: "
                        + (token == null ? "응답 없음" : token.path("error").asText()));
            }
            String accessToken = token.path("access_token").asText("");
            if (accessToken.isBlank()) {
                throw new SocialLoginException("네이버 토큰 응답에 access_token 이 없다");
            }

            JsonNode me = http.get().uri(PROFILE)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                    .retrieve()
                    .body(JsonNode.class);
            if (me == null || !"00".equals(me.path("resultcode").asText())) {
                throw new SocialLoginException("네이버 회원 정보 조회 실패");
            }
            JsonNode r = me.path("response");
            String id = r.path("id").asText("");
            if (id.isBlank()) {
                throw new SocialLoginException("네이버 회원 정보 응답에 id 가 없다");
            }
            String email = KakaoLoginClient.text(r, "email");
            String name = KakaoLoginClient.firstNonBlank(
                    KakaoLoginClient.text(r, "name"), KakaoLoginClient.text(r, "nickname"));

            return new SocialProfile(PROVIDER, id, email, email != null, name);
        } catch (RestClientException e) {
            throw new SocialLoginException("네이버 통신 실패: " + e.getMessage(), e);
        }
    }
}
