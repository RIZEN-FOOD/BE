package com.rizenfood.api.popup;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 공개 팝업 API. 지금 떠야 하는 팝업만 순서대로 나간다. */
@RestController
@RequestMapping("/api/popups")
public class PopupController {

    private final PopupService service;

    public PopupController(PopupService service) {
        this.service = service;
    }

    @GetMapping
    public List<PopupDtos.PublicItem> list() {
        return service.listActive();
    }
}
