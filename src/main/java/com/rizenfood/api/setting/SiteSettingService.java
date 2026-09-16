package com.rizenfood.api.setting;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.rizenfood.api.setting.dto.SiteSettingDtos;

/**
 * 사이트 설정 읽기·쓰기.
 *
 * 공개 조회(/api/settings)는 로그인 없이 누구나 부른다. 그래서 테이블에 있는 값을 전부
 * 내보내지 않고, 화면에 쓰려고 만든 키만 내보낸다 — 나중에 누군가 내부 연락처나
 * 업체 키 같은 값을 여기 저장해도 그대로 새어 나가지 않게 하는 방어선이다.
 * (비밀값은 애초에 .env 에 둔다.)
 */
@Service
public class SiteSettingService {

    /**
     * 공개해도 되는 키의 앞부분.
     * 새 키를 공개 화면에서 쓰려면 여기에 접두사를 추가한다 — 기본은 비공개다.
     */
    private static final Set<String> PUBLIC_PREFIXES = Set.of(
            "company.",   // 사업자정보 (전자상거래법상 게시 의무)
            "sns.",       // 공식 채널 링크
            "order.",     // 주문 마감 시각·비회원 주문 허용
            "shipping.",  // 택배사·반품지·도서산간 안내
            "main.",      // 메인 화면 구성
            "auth.");     // 로그인·가입 화면 배경

    /** 주소를 넣는 칸. 스크립트 주소(javascript:)가 들어가면 화면에서 그대로 링크가 된다. */
    private static final Set<String> URL_KEYS = Set.of(
            "sns.instagram", "sns.youtube", "sns.blog",
            "auth.login_image", "auth.signup_image", "main.hero_images");

    private final SiteSettingRepository repository;

    public SiteSettingService(SiteSettingRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public Map<String, String> getPublicMap() {
        return repository.findAll().stream()
                .filter(s -> s.getValue() != null)
                .filter(s -> isPublic(s.getKey()))
                .collect(java.util.stream.Collectors.toMap(SiteSetting::getKey, SiteSetting::getValue));
    }

    @Transactional(readOnly = true)
    public List<SiteSettingDtos.AdminItem> listForAdmin() {
        return repository.findAll().stream()
                .sorted((a, b) -> a.getKey().compareTo(b.getKey()))
                .map(s -> new SiteSettingDtos.AdminItem(s.getKey(), s.getValue(), s.getDescription()))
                .toList();
    }

    /**
     * 넘어온 값만 갱신한다. 모르는 키는 조용히 건너뛴다 —
     * 관리자 화면이 미리 정해진 키만 보내므로 실수로 새 키가 생기지 않는다.
     */
    @Transactional
    public void updateValues(Map<String, String> values) {
        values.forEach((key, value) -> {
            String checked = URL_KEYS.contains(key) ? validUrls(key, value) : value;
            repository.findById(key).ifPresent(setting -> setting.updateValue(checked));
        });
    }

    private boolean isPublic(String key) {
        return PUBLIC_PREFIXES.stream().anyMatch(key::startsWith);
    }

    /**
     * 주소 칸 검사. http(s) 로 시작하는 전체 주소이거나, 우리 서버 안의 경로(/로 시작)만 받는다.
     * 쉼표로 여러 장을 넣는 칸(메인 사진)도 한 칸씩 본다.
     *
     * ★ 관리자 계정이 털렸을 때 공개 화면 전체에 스크립트를 심는 통로가 되지 않게 막는다.
     *   (푸터 SNS 링크는 모든 페이지에 나간다.)
     */
    private String validUrls(String key, String value) {
        if (value == null || value.isBlank()) {
            return value;
        }
        String cleaned = value.trim();
        for (String one : cleaned.split(",")) {
            String u = one.trim();
            if (u.isEmpty()) {
                continue;
            }
            boolean ok = u.startsWith("/") || u.startsWith("http://") || u.startsWith("https://");
            if (!ok) {
                throw new IllegalArgumentException(
                        "'" + key + "' 에는 https:// 로 시작하는 전체 주소를 넣어주세요.");
            }
        }
        return cleaned;
    }
}
