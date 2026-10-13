package com.rizenfood.api.banner;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.rizenfood.api.banner.dto.BannerDtos;
import com.rizenfood.api.banner.dto.HeroImageSlideDtos;

/**
 * 공개 배너 API. 인증이 필요 없다.
 * 노출 중인 배너만, 요청한 위치에 맞춰 나간다.
 */
@RestController
@RequestMapping("/api/banners")
public class BannerController {

    private final BannerService service;
    private final HeroImageSlideService heroImageSlides;

    public BannerController(BannerService service, HeroImageSlideService heroImageSlides) {
        this.service = service;
        this.heroImageSlides = heroImageSlides;
    }

    /** @param position MAIN_TOP | MAIN_MID | PRODUCT_TOP */
    @GetMapping
    public List<BannerDtos.PublicItem> list(@RequestParam String position) {
        return service.listActive(position);
    }

    /**
     * 메인 히어로의 이미지 한 장 슬라이드 (V41). 노출 켠 것만 순서대로.
     * 누끼 슬라이드(/api/products/hero)와 sortOrder·heroSort 로 섞어 보여준다.
     */
    @GetMapping("/hero")
    public List<HeroImageSlideDtos.PublicItem> hero() {
        return heroImageSlides.listPublic();
    }
}
