package com.rizenfood.api.setting.dto;

import java.util.Map;

import jakarta.validation.constraints.NotNull;

public final class SiteSettingDtos {

    private SiteSettingDtos() {
    }

    /** 관리자 목록 한 줄. 키가 뭘 뜻하는지 설명을 같이 준다. */
    public record AdminItem(String key, String value, String description) {
    }

    /**
     * 값 일괄 수정.
     * 키는 미리 정해진 것만 있다(V10 시드). 없는 키를 보내면 무시한다 —
     * 관리자 화면이 아는 키만 렌더하므로 실수로 새 키가 생기지 않는다.
     */
    public record UpdateRequest(@NotNull Map<String, String> values) {
    }
}
