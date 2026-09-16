package com.rizenfood.api.member.social;

/**
 * 간편 로그인 제공자 한 곳(카카오 또는 네이버)과 주고받는 부분.
 *
 * 흐름: 손님을 제공자 로그인 화면으로 보낸다(authorizeUrl) → 제공자가 인가 코드를 들고
 * 우리 콜백으로 돌려보낸다 → 서버가 그 코드로 토큰을 받고 회원 정보를 읽는다(fetchProfile).
 * 토큰 교환은 서버끼리만 한다. 브라우저에는 제공자 토큰이 남지 않는다.
 */
public interface SocialProviderClient {

    /** 주소에 쓰는 이름. "kakao" | "naver" */
    String provider();

    /** 키가 설정돼 있어 쓸 수 있는지. */
    boolean enabled();

    /** 제공자 로그인 화면 주소. state 는 우리가 만든 1회용 값이다(위조 요청 방지). */
    String authorizeUrl(String state, String redirectUri);

    /**
     * 인가 코드로 회원 정보를 받아온다.
     * @throws SocialLoginException 제공자 응답이 실패했거나 형식이 맞지 않을 때
     */
    SocialProfile fetchProfile(String code, String state, String redirectUri);
}
