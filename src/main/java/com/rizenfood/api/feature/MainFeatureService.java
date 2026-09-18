package com.rizenfood.api.feature;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.rizenfood.api.common.NotFoundException;
import com.rizenfood.api.feature.dto.MainFeatureDtos;
import com.rizenfood.api.image.ImageService;
import com.rizenfood.api.image.ImageVariant;

/**
 * 메인 FEATURES 읽기·쓰기.
 *
 * 사진은 저장 키를 그대로 내보내지 않고 공개 URL 로 바꿔 준다 (업로드 파일 서빙 경로).
 * 화면 코드에 박힌 사진 파일을 쓰지 않으므로, 사진이 없으면 없는 채로 내려간다.
 */
@Service
public class MainFeatureService {

    private final MainFeatureRepository repository;
    private final ImageService imageService;

    public MainFeatureService(MainFeatureRepository repository, ImageService imageService) {
        this.repository = repository;
        this.imageService = imageService;
    }

    @Transactional(readOnly = true)
    public List<MainFeatureDtos.PublicItem> listPublic() {
        return repository.findByVisibleTrueOrderBySortOrderAscIdAsc().stream()
                .map(f -> new MainFeatureDtos.PublicItem(
                        f.getId(), f.getTitle(), f.getBody(),
                        url(f.getImageKey(), ImageVariant.LARGE),
                        url(f.getImageMobileKey(), ImageVariant.LARGE),
                        f.getAltText() == null ? f.getTitle() : f.getAltText(),
                        f.isAutoNutritionBody()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<MainFeatureDtos.AdminItem> listForAdmin() {
        return repository.findAllByOrderBySortOrderAscIdAsc().stream()
                .map(f -> new MainFeatureDtos.AdminItem(
                        f.getId(), f.getTitle(), f.getBody(),
                        f.getImageKey(), url(f.getImageKey(), ImageVariant.MEDIUM),
                        f.getImageMobileKey(), url(f.getImageMobileKey(), ImageVariant.MEDIUM),
                        f.getAltText(), f.isAutoNutritionBody(), f.getSortOrder(), f.isVisible()))
                .toList();
    }

    @Transactional
    public Long create(MainFeatureDtos.SaveRequest r) {
        MainFeature f = new MainFeature(r.title());
        f.setSortOrder(repository.findMaxSortOrder() + 1);
        apply(f, r);
        return repository.save(f).getId();
    }

    @Transactional
    public void update(Long id, MainFeatureDtos.SaveRequest r) {
        MainFeature f = find(id);
        f.setTitle(r.title());
        apply(f, r);
    }

    @Transactional
    public void delete(Long id) {
        repository.delete(find(id));
    }

    @Transactional
    public void updateVisibility(Long id, boolean visible) {
        MainFeature f = find(id);
        f.setVisible(visible);
        f.touch();
    }

    /**
     * 화면에 보이는 차례대로 받은 id 순서를 저장한다.
     * 목록에 없는 id 는 건너뛴다 - 다른 창에서 지운 칸이 섞여 와도 순서 저장이 실패하지 않게.
     */
    @Transactional
    public void reorder(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            throw new IllegalArgumentException("순서를 바꿀 항목이 없습니다.");
        }
        Map<Long, MainFeature> byId = new HashMap<>();
        repository.findAllById(ids).forEach(f -> byId.put(f.getId(), f));

        int order = 1;
        for (Long id : ids) {
            MainFeature f = byId.get(id);
            if (f == null) {
                continue;
            }
            f.setSortOrder(order++);
            f.touch();
        }
    }

    private MainFeature find(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new NotFoundException("FEATURES 항목을 찾을 수 없습니다."));
    }

    private void apply(MainFeature f, MainFeatureDtos.SaveRequest r) {
        f.setBody(r.body());
        f.setImageKey(r.imageKey());
        f.setImageMobileKey(r.imageMobileKey());
        f.setAltText(r.altText());
        f.setAutoNutritionBody(r.autoNutritionBody());
        f.setVisible(r.visible());
        f.touch();
    }

    private String url(String baseKey, ImageVariant variant) {
        if (baseKey == null || baseKey.isBlank()) {
            return null;
        }
        return imageService.urlOf("%s_%s.webp".formatted(baseKey, variant.suffix()));
    }
}
