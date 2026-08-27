package com.rizenfood.api.banner;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.rizenfood.api.banner.dto.BannerDtos;

/**
 * 공개 배너 API. 인증이 필요 없다.
 * 노출 중인 배너만, 요청한 위치에 맞춰 나간다.
 */
@RestController
@RequestMapping("/api/banners")
public class BannerController {

    private final BannerService service;

    public BannerController(BannerService service) {
        this.service = service;
    }

    /** @param position MAIN_TOP | MAIN_MID | PRODUCT_TOP */
    @GetMapping
    public List<BannerDtos.PublicItem> list(@RequestParam String position) {
        return service.listActive(position);
    }
}
