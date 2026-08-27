package com.rizenfood.api.product;

import java.util.List;
import java.util.function.Function;

import org.springframework.stereotype.Component;

import com.rizenfood.api.image.ImageService;
import com.rizenfood.api.image.ImageVariant;
import com.rizenfood.api.product.dto.ProductDtos;

/**
 * 엔티티를 응답 형태로 옮긴다.
 *
 * 저장소 키를 그대로 내보내지 않고 공개 URL 로 바꿔서 준다.
 * 프론트가 CDN 주소 조립 규칙을 알 필요가 없어야, 나중에 저장소를 바꿔도 프론트가 안 깨진다.
 */
@Component
public class ProductMapper {

    private final ImageService imageService;

    public ProductMapper(ImageService imageService) {
        this.imageService = imageService;
    }

    public ProductDtos.ListItem toListItem(Product p) {
        return new ProductDtos.ListItem(
                p.getId(), p.getSlug(), p.getNameKo(), p.getNameEn(), p.getSubtitle(),
                p.getPrice(), p.getDiscountPrice(), p.effectivePrice(), p.getWeightG(),
                isSoldOut(p), p.isFeatured(),
                variantUrl(p.getThumbnailKey(), ImageVariant.THUMBNAIL));
    }

    public ProductDtos.AdminListItem toAdminListItem(Product p) {
        return new ProductDtos.AdminListItem(
                p.getId(), p.getSlug(), p.getNameKo(),
                p.getPrice(), p.getDiscountPrice(),
                p.getStock() == null ? 0 : p.getStock(),
                isSoldOut(p), p.isFeatured(), p.isVisible(), p.getSortOrder(),
                variantUrl(p.getThumbnailKey(), ImageVariant.THUMBNAIL));
    }

    public ProductDtos.Detail toDetail(Product p) {
        return new ProductDtos.Detail(
                p.getId(), p.getSlug(), p.getNameKo(), p.getNameEn(), p.getSubtitle(),
                p.getDescriptionHtml(),
                p.getThumbnailKey(),
                p.getPrice(), p.getDiscountPrice(), p.effectivePrice(),
                p.getWeightG(), p.getServings(), p.getStock(), isSoldOut(p),
                p.isFeatured(), p.isVisible(),
                map(p.getImages(), this::toImageItem),
                map(p.getOptions().stream().filter(ProductOption::isVisible).toList(),
                        o -> toOptionItem(p, o)),
                toNutritionItem(p.getNutrition()),
                map(p.getIngredients(), this::toIngredientItem),
                toLabelItem(p.getLabel()),
                map(p.getPurchaseLinks().stream().filter(PurchaseLink::isVisible).toList(),
                        this::toPurchaseLinkItem));
    }

    /**
     * 옵션이 있으면 옵션 재고를, 없으면 상품 재고를 본다.
     * 옵션이 전부 품절이면 상품도 품절이다.
     */
    private boolean isSoldOut(Product p) {
        List<ProductOption> visible = p.getOptions().stream().filter(ProductOption::isVisible).toList();
        if (!visible.isEmpty()) {
            return visible.stream().allMatch(o -> o.getStock() <= 0);
        }
        return p.getStock() == null || p.getStock() <= 0;
    }

    private ProductDtos.ImageItem toImageItem(ProductImage i) {
        return new ProductDtos.ImageItem(
                variantUrl(i.getImageKey(), ImageVariant.MEDIUM),
                i.getImageKey(), i.getAltText(), i.getType());
    }

    private ProductDtos.OptionItem toOptionItem(Product p, ProductOption o) {
        return new ProductDtos.OptionItem(
                o.getId(), o.getName(), p.effectivePrice() + o.getPriceDelta(),
                o.getStock(), o.getStock() <= 0);
    }

    private ProductDtos.NutritionItem toNutritionItem(Nutrition n) {
        if (n == null) {
            return null;
        }
        return new ProductDtos.NutritionItem(
                n.getServingSizeG(), n.getKcal(), n.getCarbG(),
                n.getProteinG(), n.getFatG(), n.getSugarG(), n.getSodiumMg());
    }

    private ProductDtos.IngredientItem toIngredientItem(Ingredient i) {
        return new ProductDtos.IngredientItem(
                i.getName(), i.getPercentage(), i.getOrigin(), i.getAllergen());
    }

    private ProductDtos.LabelItem toLabelItem(ProductLabel l) {
        if (l == null) {
            return null;
        }
        return new ProductDtos.LabelItem(
                l.getFoodType(), l.getShelfLife(), l.getStorageMethod(),
                l.getManufacturer(), l.getManufacturerAddr(),
                l.getSeller(), l.getSellerAddr(),
                l.getCustomerService(), l.getPackageMaterial(), l.getExtraNotice());
    }

    private ProductDtos.PurchaseLinkItem toPurchaseLinkItem(PurchaseLink l) {
        return new ProductDtos.PurchaseLinkItem(l.getChannel(), l.getUrl(), l.getLabel());
    }

    /**
     * 업로드가 만든 기본 키에 크기 접미사를 붙여 URL 을 만든다.
     * 키가 없으면 null 을 준다. 프론트가 대체 이미지를 쓴다.
     */
    private String variantUrl(String baseKey, ImageVariant variant) {
        if (baseKey == null || baseKey.isBlank()) {
            return null;
        }
        return imageService.urlOf("%s_%s.webp".formatted(baseKey, variant.suffix()));
    }

    private <S, T> List<T> map(List<S> source, Function<S, T> fn) {
        return source.stream().map(fn).toList();
    }
}
