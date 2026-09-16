package com.rizenfood.api.feature;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.rizenfood.api.feature.dto.MainFeatureDtos;

/**
 * 공개 메인 FEATURES API. 인증이 필요 없다. 노출 중인 칸만 순서대로 나간다.
 */
@RestController
@RequestMapping("/api/main-features")
public class MainFeatureController {

    private final MainFeatureService service;

    public MainFeatureController(MainFeatureService service) {
        this.service = service;
    }

    @GetMapping
    public List<MainFeatureDtos.PublicItem> list() {
        return service.listPublic();
    }
}
