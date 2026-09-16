package com.rizenfood.api.member.social;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 간편 로그인(카카오·네이버) 설정. 값은 application.yml 의 app.oauth.* 에 있다.
 *
 * 키(client id·secret)는 .env 로만 넣는다. 키가 비어 있는 제공자는 꺼진 것으로 보고
 * 화면에 버튼을 띄우지 않는다 — 누르면 실패하는 버튼을 상용 화면에 두지 않는다.
 *
 * redirectBase 는 손님이 보는 사이트 주소다(예: https://www.rizenfood.co.kr).
 * 카카오·네이버가 로그인 뒤 돌려보낼 주소를 여기서 만든다. 개발자 콘솔에 등록한 주소와
 * 글자 하나까지 같아야 한다 (docs: BE/deploy/SOCIAL_LOGIN.md).
 */
@ConfigurationProperties(prefix = "app.oauth")
public record SocialProperties(String redirectBase, Client kakao, Client naver) {

    public SocialProperties {
        if (redirectBase == null || redirectBase.isBlank()) {
            redirectBase = "http://localhost:3200";
        }
        redirectBase = redirectBase.replaceAll("/+$", "");
        if (kakao == null) {
            kakao = new Client(null, null);
        }
        if (naver == null) {
            naver = new Client(null, null);
        }
    }

    /** 제공자가 로그인 뒤 돌려보낼 주소. 화면 서버를 거쳐 API 로 들어온다(/api 중계). */
    public String callbackUrl(String provider) {
        return redirectBase + "/api/auth/oauth/" + provider + "/callback";
    }

    public record Client(String clientId, String clientSecret) {

        public boolean enabled() {
            return clientId != null && !clientId.isBlank();
        }

        public boolean hasSecret() {
            return clientSecret != null && !clientSecret.isBlank();
        }
    }
}
