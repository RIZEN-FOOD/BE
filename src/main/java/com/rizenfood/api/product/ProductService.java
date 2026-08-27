package com.rizenfood.api.product;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.rizenfood.api.common.HtmlSanitizer;
import com.rizenfood.api.common.NotFoundException;
import com.rizenfood.api.product.dto.ProductDtos;

/**
 * 상품 읽기·쓰기.
 *
 * 공개 조회는 visible=true 만 본다. 숨긴 상품은 존재 자체를 알리지 않는다.
 * 관리자 조회는 전부 본다.
 */
@Service
public class ProductService {

    private final ProductRepository repository;
    private final ProductMapper mapper;
    private final HtmlSanitizer sanitizer;

    public ProductService(ProductRepository repository, ProductMapper mapper, HtmlSanitizer sanitizer) {
        this.repository = repository;
        this.mapper = mapper;
        this.sanitizer = sanitizer;
    }

    // ── 공개 ─────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Page<ProductDtos.ListItem> listPublic(Pageable pageable) {
        return repository.findByVisibleTrue(pageable).map(mapper::toListItem);
    }

    @Transactional(readOnly = true)
    public List<ProductDtos.ListItem> listFeatured() {
        return repository.findByVisibleTrueAndFeaturedTrueOrderBySortOrderAscIdAsc()
                .stream().map(mapper::toListItem).toList();
    }

    @Transactional(readOnly = true)
    public ProductDtos.Detail getPublic(String slug) {
        // 숨긴 상품도 404 다. 403 을 주면 "그 주소에 뭔가 있다"는 걸 알려주는 셈이다.
        return repository.findBySlugAndVisibleTrue(slug)
                .map(mapper::toDetail)
                .orElseThrow(() -> new NotFoundException("상품을 찾을 수 없습니다."));
    }

    // ── 관리자 ───────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Page<ProductDtos.ListItem> listForAdmin(Pageable pageable) {
        return repository.findAll(pageable).map(mapper::toListItem);
    }

    @Transactional(readOnly = true)
    public ProductDtos.Detail getForAdmin(Long id) {
        return repository.findWithDetailsById(id)
                .map(mapper::toDetail)
                .orElseThrow(() -> new NotFoundException("상품을 찾을 수 없습니다."));
    }

    @Transactional
    public Long create(ProductDtos.SaveRequest request) {
        if (repository.existsBySlug(request.slug())) {
            throw new DuplicateSlugException(request.slug());
        }
        Product product = new Product(request.slug(), request.nameKo(), request.price());
        // 새 상품은 목록 맨 뒤에 붙인다.
        product.setSortOrder(repository.findMaxSortOrder() + 1);
        apply(product, request);
        return repository.save(product).getId();
    }

    @Transactional
    public void update(Long id, ProductDtos.SaveRequest request) {
        Product product = repository.findWithDetailsById(id)
                .orElseThrow(() -> new NotFoundException("상품을 찾을 수 없습니다."));

        if (!product.getSlug().equals(request.slug()) && repository.existsBySlug(request.slug())) {
            throw new DuplicateSlugException(request.slug());
        }
        product.setSlug(request.slug());
        apply(product, request);
    }

    @Transactional
    public void delete(Long id) {
        Product product = repository.findById(id)
                .orElseThrow(() -> new NotFoundException("상품을 찾을 수 없습니다."));
        repository.delete(product);
    }

    /** 드래그 정렬. 받은 순서대로 sort_order 를 다시 매긴다. */
    @Transactional
    public void reorder(List<Long> orderedIds) {
        List<Product> products = repository.findAllById(orderedIds);
        if (products.size() != orderedIds.size()) {
            throw new NotFoundException("정렬 대상 중 없는 상품이 있습니다.");
        }
        for (int i = 0; i < orderedIds.size(); i++) {
            Long id = orderedIds.get(i);
            int order = i;
            products.stream()
                    .filter(p -> p.getId().equals(id))
                    .findFirst()
                    .ifPresent(p -> { p.setSortOrder(order); p.touch(); });
        }
    }

    /** 노출 · 메인노출 토글. 넘어온 값만 바꾼다. */
    @Transactional
    public void updateVisibility(Long id, Boolean visible, Boolean featured) {
        Product product = repository.findById(id)
                .orElseThrow(() -> new NotFoundException("상품을 찾을 수 없습니다."));
        if (visible != null) {
            product.setVisible(visible);
        }
        if (featured != null) {
            product.setFeatured(featured);
        }
        product.touch();
    }

    /** 요청 값을 엔티티에 옮긴다. */
    private void apply(Product p, ProductDtos.SaveRequest r) {
        if (r.discountPrice() != null && r.discountPrice() > r.price()) {
            throw new IllegalArgumentException("할인가가 정가보다 클 수 없습니다.");
        }

        p.setNameKo(r.nameKo());
        p.setNameEn(r.nameEn());
        p.setSubtitle(r.subtitle());

        // ★ 에디터 입력은 반드시 여기를 거친다. 저장형 XSS 의 주요 경로다.
        p.setDescriptionHtml(sanitizer.clean(r.descriptionHtml()));

        p.setPrice(r.price());
        p.setDiscountPrice(r.discountPrice());
        p.setWeightG(r.weightG());
        p.setServings(r.servings());
        p.setStock(r.stock() == null ? 0 : r.stock());
        p.setThumbnailKey(r.thumbnailKey());
        p.setFeatured(r.featured());
        p.setVisible(r.visible());
        p.touch();

        if (r.images() != null) {
            p.replaceImages(r.images().stream()
                    .map(i -> new ProductImage(i.imageKey(), i.altText(),
                            i.type() == null ? "DETAIL" : i.type(), i.sortOrder()))
                    .toList());
        }
        if (r.options() != null) {
            p.replaceOptions(r.options().stream()
                    .map(o -> new ProductOption(o.name(), o.priceDelta(), o.stock(), o.sortOrder(), o.visible()))
                    .toList());
        }
        if (r.ingredients() != null) {
            p.replaceIngredients(r.ingredients().stream()
                    .map(i -> new Ingredient(i.name(), i.percentage(), i.origin(), i.allergen(), i.sortOrder()))
                    .toList());
        }
        if (r.purchaseLinks() != null) {
            p.replacePurchaseLinks(r.purchaseLinks().stream()
                    .map(l -> new PurchaseLink(l.channel(), l.url(), l.label(), l.sortOrder(), l.visible()))
                    .toList());
        }
        if (r.nutrition() != null) {
            var n = r.nutrition();
            p.setNutrition(new Nutrition(n.servingSizeG(), n.kcal(), n.carbG(),
                    n.proteinG(), n.fatG(), n.sugarG(), n.sodiumMg()));
        }
        if (r.label() != null) {
            var l = r.label();
            ProductLabel label = new ProductLabel();
            label.setFoodType(l.foodType());
            label.setShelfLife(l.shelfLife());
            label.setStorageMethod(l.storageMethod());
            label.setManufacturer(l.manufacturer());
            label.setManufacturerAddr(l.manufacturerAddr());
            label.setSeller(l.seller());
            label.setSellerAddr(l.sellerAddr());
            label.setCustomerService(l.customerService());
            label.setPackageMaterial(l.packageMaterial());
            label.setExtraNotice(sanitizer.clean(l.extraNotice()));
            p.setLabel(label);
        }
    }

    /** 주소 이름이 이미 쓰이고 있을 때 */
    public static class DuplicateSlugException extends RuntimeException {
        public DuplicateSlugException(String slug) {
            super("\"" + slug + "\" 은(는) 이미 사용 중인 주소 이름입니다. 다른 이름을 써 주세요.");
        }
    }
}
