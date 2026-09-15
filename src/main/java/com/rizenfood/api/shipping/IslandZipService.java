package com.rizenfood.api.shipping;

import org.springframework.stereotype.Service;

import com.rizenfood.api.setting.SiteSetting;
import com.rizenfood.api.setting.SiteSettingRepository;

/**
 * 우편번호가 도서산간인지 판정한다.
 *
 * 목록은 사이트 설정 shipping.island_zip_ranges 에서 읽고, 비었거나 읽을 수 있는 번호가 하나도 없으면
 * 기본 목록({@link IslandZipRanges#DEFAULT})을 쓴다 — 설정 실수로 도서산간 판정이 통째로 꺼지지 않게.
 */
@Service
public class IslandZipService {

    public static final String SETTING_KEY = "shipping.island_zip_ranges";

    private final SiteSettingRepository settings;

    public IslandZipService(SiteSettingRepository settings) {
        this.settings = settings;
    }

    public boolean isIsland(String zipcode) {
        return ranges().contains(zipcode);
    }

    private IslandZipRanges ranges() {
        String spec = settings.findById(SETTING_KEY).map(SiteSetting::getValue).orElse(null);
        if (spec == null || spec.isBlank()) {
            return IslandZipRanges.DEFAULT;
        }
        IslandZipRanges custom = IslandZipRanges.parse(spec);
        return custom.isEmpty() ? IslandZipRanges.DEFAULT : custom;
    }
}
