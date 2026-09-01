package com.rizenfood.api.setting;

import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.rizenfood.api.setting.dto.SiteSettingDtos;

/**
 * 사이트 설정 읽기·쓰기.
 *
 * 공개 조회는 키-값 맵 그대로 준다. 여기 저장하는 값은 사업자정보·SNS 링크처럼
 * 애초에 공개돼야 하는 정보다 — 비밀값을 여기 넣지 않는다(그런 값은 .env 다).
 */
@Service
public class SiteSettingService {

    private final SiteSettingRepository repository;

    public SiteSettingService(SiteSettingRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public Map<String, String> getPublicMap() {
        return repository.findAll().stream()
                .filter(s -> s.getValue() != null)
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
        values.forEach((key, value) ->
                repository.findById(key).ifPresent(setting -> setting.updateValue(value)));
    }
}
