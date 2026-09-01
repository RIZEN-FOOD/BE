package com.rizenfood.api.setting;

import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 공개 사이트 설정 조회. 인증이 필요 없다. 푸터·정책 페이지가 이걸 쓴다. */
@RestController
@RequestMapping("/api/settings")
public class SiteSettingController {

    private final SiteSettingService service;

    public SiteSettingController(SiteSettingService service) {
        this.service = service;
    }

    @GetMapping
    public Map<String, String> get() {
        return service.getPublicMap();
    }
}
