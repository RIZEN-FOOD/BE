package com.rizenfood.api.setting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

/**
 * 사이트 설정의 두 가지 방어선.
 *
 *  1) 공개 조회는 화면에 쓰려고 만든 키만 내보낸다 (실수로 저장된 값이 새지 않게).
 *  2) 주소 칸에는 주소만 들어간다 (관리자 계정이 털려도 공개 화면에 스크립트를 못 심게).
 */
class SiteSettingServiceTest {

    private SiteSettingRepository repository;
    private SiteSettingService service;

    private SiteSetting setting(String key, String value) {
        SiteSetting s = Mockito.mock(SiteSetting.class);
        Mockito.lenient().when(s.getKey()).thenReturn(key);
        Mockito.lenient().when(s.getValue()).thenReturn(value);
        return s;
    }

    @BeforeEach
    void setUp() {
        repository = Mockito.mock(SiteSettingRepository.class);
        service = new SiteSettingService(repository);
    }

    @Test
    @DisplayName("공개 조회에는 정해둔 키만 나간다")
    void publicMapOnlyKnownKeys() {
        // 목 안에서 목을 만들면 스터빙이 꼬인다. 먼저 만들어 두고 넘긴다.
        List<SiteSetting> rows = List.of(
                setting("company.tel", "070-8098-9542"),
                setting("shipping.carrier", "롯데택배"),
                setting("internal.partner_key", "비밀값"));
        Mockito.when(repository.findAll()).thenReturn(rows);

        Map<String, String> map = service.getPublicMap();

        assertThat(map).containsKeys("company.tel", "shipping.carrier");
        assertThat(map).doesNotContainKey("internal.partner_key");
    }

    @Test
    @DisplayName("주소 칸에 스크립트를 넣으면 저장하지 않는다")
    void rejectsScriptUrls() {
        assertThatThrownBy(() -> service.updateValues(
                Map.of("sns.instagram", "javascript:fetch('/api/member/me')")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("sns.instagram");

        Mockito.verify(repository, Mockito.never()).findById(Mockito.anyString());
    }

    @Test
    @DisplayName("정상 주소와 우리 서버 경로는 저장된다")
    void acceptsHttpAndLocalPaths() {
        SiteSetting sns = setting("sns.instagram", "");
        SiteSetting image = setting("auth.login_image", "");
        Mockito.when(repository.findById("sns.instagram")).thenReturn(Optional.of(sns));
        Mockito.when(repository.findById("auth.login_image")).thenReturn(Optional.of(image));

        service.updateValues(Map.of("sns.instagram", "https://instagram.com/rizenfood"));
        service.updateValues(Map.of("auth.login_image", "/uploads/main/2026/09/abc.webp"));

        Mockito.verify(sns).updateValue("https://instagram.com/rizenfood");
        Mockito.verify(image).updateValue("/uploads/main/2026/09/abc.webp");
    }
}
