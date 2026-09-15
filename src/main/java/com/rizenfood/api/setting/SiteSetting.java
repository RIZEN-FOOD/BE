package com.rizenfood.api.setting;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 사이트 설정. 키-값 저장소.
 *
 * 사업자정보·SNS 링크·메인 섹션 노출 여부 등을 코드에 박지 않고 여기서 관리한다.
 * 대표가 관리자 화면에서 값만 바꾼다.
 *
 * 초기 키 목록은 Flyway 시드(V10)에 있다. 여기서는 값만 갱신한다 — 키를
 * 새로 만들지 않는다. 새 설정 항목이 필요하면 마이그레이션을 먼저 추가한다.
 */
@Entity
@Table(name = "site_setting")
public class SiteSetting {

    @Id
    @Column(length = 100)
    private String key;

    @Column(columnDefinition = "text")
    private String value;

    @Column(length = 300)
    private String description;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    protected SiteSetting() {
    }

    public void updateValue(String value) {
        this.value = value;
        this.updatedAt = Instant.now();
    }

    public String getKey() { return key; }
    public String getValue() { return value; }
    public String getDescription() { return description; }
    public Instant getUpdatedAt() { return updatedAt; }
}
