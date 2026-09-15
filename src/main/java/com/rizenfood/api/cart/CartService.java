package com.rizenfood.api.cart;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.rizenfood.api.cart.dto.CartDtos;
import com.rizenfood.api.common.NotFoundException;
import com.rizenfood.api.image.ImageService;
import com.rizenfood.api.product.Product;
import com.rizenfood.api.product.ProductOption;
import com.rizenfood.api.product.ProductRepository;
import com.rizenfood.api.shipping.ShippingPolicy;
import com.rizenfood.api.shipping.ShippingPolicyRepository;

/**
 * 장바구니.
 *
 * ★ 금액과 재고는 언제나 서버가 상품 테이블에서 다시 읽어 계산한다
 *   (CLAUDE.md 규칙 5). 담아둔 사이 가격이 바뀌었거나 품절됐을 수 있으므로,
 *   장바구니를 볼 때마다 현재 상태로 다시 그린다. 클라이언트가 보낸 금액은 믿지 않는다.
 *
 * 배송비는 shipping_policy 에서 읽는다. 임계액을 코드에 박지 않는다.
 */
@Service
public class CartService {

    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final ProductRepository productRepository;
    private final ShippingPolicyRepository shippingPolicyRepository;
    private final ImageService imageService;

    public CartService(CartRepository cartRepository,
                       CartItemRepository cartItemRepository,
                       ProductRepository productRepository,
                       ShippingPolicyRepository shippingPolicyRepository,
                       ImageService imageService) {
        this.cartRepository = cartRepository;
        this.cartItemRepository = cartItemRepository;
        this.productRepository = productRepository;
        this.shippingPolicyRepository = shippingPolicyRepository;
        this.imageService = imageService;
    }

    // ── 장바구니 확보 ──────────────────────────────────────────

    /** 회원 장바구니. 없으면 만든다. */
    @Transactional
    public Cart resolveMemberCart(Long memberId) {
        return cartRepository.findByMemberId(memberId)
                .orElseGet(() -> cartRepository.save(Cart.forMember(memberId)));
    }

    /** 게스트 장바구니를 있는 그대로 찾는다(없으면 비어 있음). 주문 시 조회용. */
    @Transactional(readOnly = true)
    public Optional<Cart> findGuestCart(String guestToken) {
        if (guestToken == null || guestToken.isBlank()) {
            return Optional.empty();
        }
        return cartRepository.findByGuestToken(guestToken);
    }

    /** 게스트 장바구니. 토큰이 없거나 해당 장바구니가 없으면 새로 만든다. */
    @Transactional
    public Cart resolveGuestCart(String guestToken, String freshTokenIfMissing) {
        if (guestToken != null && !guestToken.isBlank()) {
            Optional<Cart> found = cartRepository.findByGuestToken(guestToken);
            if (found.isPresent()) {
                return found.get();
            }
        }
        return cartRepository.save(Cart.forGuest(freshTokenIfMissing));
    }

    // ── 담기·수정·삭제 ────────────────────────────────────────

    @Transactional
    public void add(Long cartId, CartDtos.AddRequest req) {
        // 컨트롤러가 넘겨준 장바구니는 다른 트랜잭션에서 조회돼 분리(detached)돼 있다.
        // 이 트랜잭션에서 다시 붙여 managed 상태로 다룬다.
        Cart cart = getManaged(cartId);

        Product product = productRepository.findById(req.productId())
                .orElseThrow(() -> new NotFoundException("상품을 찾을 수 없습니다."));
        if (!product.isVisible()) {
            throw new IllegalArgumentException("현재 판매하지 않는 상품입니다.");
        }

        ProductOption option = resolveOption(product, req.optionId());
        int availableStock = option != null ? option.getStock() : product.getStock();

        // 같은 상품·옵션이 이미 담겨 있으면 수량을 더한다.
        CartItem existing = findLine(cartId, product.getId(), req.optionId());
        int targetQty = (existing != null ? existing.getQuantity() : 0) + req.quantity();

        if (targetQty > CartDtos.MAX_QTY) {
            throw new IllegalArgumentException(
                    "한 상품은 최대 " + CartDtos.MAX_QTY + "개까지 담을 수 있습니다.");
        }
        if (availableStock < targetQty) {
            throw new IllegalArgumentException(
                    availableStock <= 0 ? "품절된 상품입니다."
                            : "재고가 부족합니다. 남은 수량은 " + availableStock + "개입니다.");
        }

        if (existing != null) {
            existing.addQuantity(req.quantity());
        } else {
            cartItemRepository.save(new CartItem(cart, product, option, req.quantity()));
        }
        cart.touch();
    }

    @Transactional
    public void updateQuantity(Long cartId, Long itemId, int quantity) {
        CartItem item = cartItemRepository.findByCartIdAndId(cartId, itemId)
                .orElseThrow(() -> new NotFoundException("장바구니 항목을 찾을 수 없습니다."));

        ProductOption option = item.getOption();
        int availableStock = option != null ? option.getStock() : item.getProduct().getStock();
        if (availableStock < quantity) {
            throw new IllegalArgumentException(
                    availableStock <= 0 ? "품절된 상품입니다."
                            : "재고가 부족합니다. 남은 수량은 " + availableStock + "개입니다.");
        }
        item.setQuantity(quantity);
        getManaged(cartId).touch();
    }

    @Transactional
    public void remove(Long cartId, Long itemId) {
        CartItem item = cartItemRepository.findByCartIdAndId(cartId, itemId)
                .orElseThrow(() -> new NotFoundException("장바구니 항목을 찾을 수 없습니다."));
        cartItemRepository.delete(item);
        getManaged(cartId).touch();
    }

    @Transactional
    public void clear(Long cartId) {
        List<CartItem> items = cartItemRepository.findByCartIdOrderByAddedAtAsc(cartId);
        cartItemRepository.deleteAll(items);
        getManaged(cartId).touch();
    }

    // ── 로그인 시 병합 ────────────────────────────────────────

    /**
     * 게스트 장바구니를 회원 장바구니에 병합하고 게스트 장바구니를 지운다.
     * 같은 상품·옵션은 수량을 더하되 최대 수량으로 자른다.
     * 로그인 직후 회원이 자기 장바구니를 볼 때 호출한다.
     */
    @Transactional
    public void mergeGuestInto(String guestToken, Long memberId) {
        if (guestToken == null || guestToken.isBlank()) {
            return;
        }
        Optional<Cart> guestOpt = cartRepository.findByGuestToken(guestToken);
        if (guestOpt.isEmpty()) {
            return;
        }
        Cart guest = guestOpt.get();
        Cart member = resolveMemberCart(memberId);

        List<CartItem> guestItems =
                cartItemRepository.findByCartIdOrderByAddedAtAsc(guest.getId());
        for (CartItem g : guestItems) {
            Long optionId = g.getOption() != null ? g.getOption().getId() : null;
            CartItem existing = findLine(member.getId(), g.getProduct().getId(), optionId);
            int merged = (existing != null ? existing.getQuantity() : 0) + g.getQuantity();
            merged = Math.min(merged, CartDtos.MAX_QTY);

            if (existing != null) {
                existing.setQuantity(merged);
            } else {
                cartItemRepository.save(
                        new CartItem(member, g.getProduct(), g.getOption(), merged));
            }
        }
        member.touch();

        // 게스트 장바구니 제거 (cart_item 은 FK ON DELETE CASCADE)
        cartItemRepository.deleteAll(guestItems);
        cartRepository.delete(guest);
    }

    // ── 조회 (금액·재고 재계산) ───────────────────────────────

    @Transactional(readOnly = true)
    public CartDtos.CartView view(Long cartId) {
        List<CartItem> items = cartItemRepository.findByCartIdOrderByAddedAtAsc(cartId);

        List<CartDtos.ItemView> views = new ArrayList<>(items.size());
        int itemsAmount = 0;
        int totalQuantity = 0;
        boolean hasUnavailable = false;

        for (CartItem item : items) {
            Product product = item.getProduct();
            ProductOption option = item.getOption();

            int unitPrice = effectiveUnitPrice(product, option);
            int qty = item.getQuantity();
            int lineAmount = unitPrice * qty;

            boolean visible = product.isVisible() && (option == null || option.isVisible());
            int stock = option != null ? option.getStock() : product.getStock();

            boolean available;
            String reason;
            if (!visible) {
                available = false;
                reason = "판매하지 않는 상품입니다.";
            } else if (stock <= 0) {
                available = false;
                reason = "품절되었습니다.";
            } else if (stock < qty) {
                available = false;
                reason = "재고가 부족합니다. 남은 수량 " + stock + "개.";
            } else {
                available = true;
                reason = null;
            }

            if (available) {
                itemsAmount += lineAmount;
                totalQuantity += qty;
            } else {
                hasUnavailable = true;
            }

            views.add(new CartDtos.ItemView(
                    item.getId(),
                    product.getId(),
                    product.getSlug(),
                    product.getNameKo(),
                    option != null ? option.getId() : null,
                    option != null ? option.getName() : null,
                    thumbnailUrl(product),
                    unitPrice,
                    qty,
                    lineAmount,
                    available,
                    stock,
                    reason));
        }

        ShippingPolicy policy = shippingPolicyRepository
                .findFirstByVisibleTrueOrderByIdAsc().orElse(null);
        int shippingFee = policy != null ? policy.feeFor(itemsAmount) : 0;
        Integer threshold = policy != null ? policy.getFreeThreshold() : null;
        int freeRemaining = 0;
        if (threshold != null && itemsAmount > 0 && itemsAmount < threshold) {
            freeRemaining = threshold - itemsAmount;
        }

        return new CartDtos.CartView(
                views,
                totalQuantity,
                itemsAmount,
                shippingFee,
                threshold,
                freeRemaining,
                itemsAmount + shippingFee,
                hasUnavailable);
    }

    // ── 내부 ──────────────────────────────────────────────────

    /** 할인가가 있으면 할인가, 없으면 정가. 옵션 가격차를 더한다. */
    private int effectiveUnitPrice(Product product, ProductOption option) {
        int base = product.getDiscountPrice() != null
                ? product.getDiscountPrice() : product.getPrice();
        int delta = option != null ? option.getPriceDelta() : 0;
        return base + delta;
    }

    private String thumbnailUrl(Product product) {
        String key = product.getThumbnailKey();
        if (key == null || key.isBlank()) {
            return null;
        }
        return imageService.urlOf(key + "_thumb.webp");
    }

    /**
     * 상품에 속한, 보이는 옵션을 찾는다.
     * 옵션 없는 상품에 optionId 를 주면 오류. 옵션 있는 상품에 안 주면 오류.
     */
    private ProductOption resolveOption(Product product, Long optionId) {
        List<ProductOption> options = product.getOptions().stream()
                .filter(ProductOption::isVisible).toList();

        if (optionId == null) {
            if (!options.isEmpty()) {
                throw new IllegalArgumentException("옵션을 선택해 주세요.");
            }
            return null;
        }
        return options.stream()
                .filter(o -> o.getId().equals(optionId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("선택한 옵션을 찾을 수 없습니다."));
    }

    /** 이 트랜잭션에 붙은 managed 장바구니. touch() 로 updatedAt 을 남기는 데 쓴다. */
    private Cart getManaged(Long cartId) {
        return cartRepository.findById(cartId)
                .orElseThrow(() -> new NotFoundException("장바구니를 찾을 수 없습니다."));
    }

    private CartItem findLine(Long cartId, Long productId, Long optionId) {
        List<CartItem> items = cartItemRepository.findByCartIdOrderByAddedAtAsc(cartId);
        for (CartItem item : items) {
            Long itemOptionId = item.getOption() != null ? item.getOption().getId() : null;
            if (item.getProduct().getId().equals(productId)
                    && Objects.equals(itemOptionId, optionId)) {
                return item;
            }
        }
        return null;
    }
}
