package com.rizenfood.api.order;

import java.util.ArrayList;
import java.util.List;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.rizenfood.api.cart.CartItem;
import com.rizenfood.api.cart.CartItemRepository;
import com.rizenfood.api.common.NotFoundException;
import com.rizenfood.api.image.ImageService;
import com.rizenfood.api.member.PhoneCipher;
import com.rizenfood.api.order.dto.OrderDtos;
import com.rizenfood.api.payment.Payment;
import com.rizenfood.api.payment.PaymentGateway;
import com.rizenfood.api.payment.PaymentRepository;
import com.rizenfood.api.product.Product;
import com.rizenfood.api.product.ProductOption;
import com.rizenfood.api.product.ProductOptionRepository;
import com.rizenfood.api.product.ProductRepository;
import com.rizenfood.api.shipping.ShippingPolicy;
import com.rizenfood.api.shipping.ShippingPolicyRepository;

/**
 * 주문.
 *
 * ★ 커머스 정합성(CLAUDE.md 규칙 5)을 코드로 강제하는 곳이다.
 *   - 금액은 클라이언트 값을 쓰지 않고, 서버가 상품 테이블을 다시 읽어 계산한다.
 *   - 재고는 원자적으로 차감한다 (UPDATE ... WHERE stock >= qty). 읽고-쓰기 금지.
 *   - 주문번호는 추측 불가능하게 만든다.
 *   - 배송지·상품명·가격은 스냅샷으로 박는다.
 *   - PG 승인 금액과 서버 계산 금액을 대조한 뒤에만 주문을 확정한다.
 */
@Service
public class OrderService {

    private static final int ORDER_NO_RETRY = 5;

    private final CartItemRepository cartItemRepository;
    private final ProductRepository productRepository;
    private final ProductOptionRepository optionRepository;
    private final OrderRepository orderRepository;
    private final StockLedgerRepository stockLedgerRepository;
    private final PaymentRepository paymentRepository;
    private final ShippingPolicyRepository shippingPolicyRepository;
    private final ImageService imageService;
    private final PhoneCipher phoneCipher;
    private final OrderNoGenerator orderNoGenerator;
    private final PaymentGateway paymentGateway;

    public OrderService(CartItemRepository cartItemRepository,
                        ProductRepository productRepository,
                        ProductOptionRepository optionRepository,
                        OrderRepository orderRepository,
                        StockLedgerRepository stockLedgerRepository,
                        PaymentRepository paymentRepository,
                        ShippingPolicyRepository shippingPolicyRepository,
                        ImageService imageService,
                        PhoneCipher phoneCipher,
                        OrderNoGenerator orderNoGenerator,
                        PaymentGateway paymentGateway) {
        this.cartItemRepository = cartItemRepository;
        this.productRepository = productRepository;
        this.optionRepository = optionRepository;
        this.orderRepository = orderRepository;
        this.stockLedgerRepository = stockLedgerRepository;
        this.paymentRepository = paymentRepository;
        this.shippingPolicyRepository = shippingPolicyRepository;
        this.imageService = imageService;
        this.phoneCipher = phoneCipher;
        this.orderNoGenerator = orderNoGenerator;
        this.paymentGateway = paymentGateway;
    }

    /** 재고 부족으로 주문을 만들 수 없을 때. 409 로 매핑된다. */
    public static class OutOfStockException extends RuntimeException {
        public OutOfStockException(String message) { super(message); }
    }

    // ── 주문 생성 ─────────────────────────────────────────────

    @Transactional
    public OrderDtos.OrderView createFromCart(Long cartId, Long memberId,
                                              OrderDtos.CreateRequest req) {
        List<CartItem> cartItems = cartItemRepository.findByCartIdOrderByAddedAtAsc(cartId);
        if (cartItems.isEmpty()) {
            throw new IllegalArgumentException("장바구니가 비어 있습니다.");
        }

        // 서버가 상품 테이블을 다시 읽어 금액을 계산하고, 주문 가능한 항목만 추린다.
        List<Line> lines = new ArrayList<>();
        for (CartItem ci : cartItems) {
            Product product = ci.getProduct();
            ProductOption option = ci.getOption();
            boolean visible = product.isVisible() && (option == null || option.isVisible());
            int stock = option != null ? option.getStock() : product.getStock();
            if (!visible || stock <= 0 || stock < ci.getQuantity()) {
                continue; // 품절·판매중지·재고부족 항목은 주문에서 제외
            }
            int unitPrice = effectiveUnitPrice(product, option);
            lines.add(new Line(product, option, unitPrice, ci.getQuantity(), ci.getId()));
        }
        if (lines.isEmpty()) {
            throw new IllegalArgumentException("주문할 수 있는 상품이 없습니다. 재고를 확인해 주세요.");
        }

        // 재고 원자적 차감. 하나라도 실패하면 예외 → 트랜잭션 롤백으로 앞선 차감도 되돌아간다.
        for (Line ln : lines) {
            int affected = (ln.option != null)
                    ? optionRepository.decreaseStock(ln.option.getId(), ln.qty)
                    : productRepository.decreaseStock(ln.product.getId(), ln.qty);
            if (affected == 0) {
                throw new OutOfStockException(
                        "'" + ln.product.getNameKo() + "' 재고가 부족합니다. 다시 시도해 주세요.");
            }
        }

        // 금액 계산
        int itemsAmount = lines.stream().mapToInt(l -> l.unitPrice * l.qty).sum();
        ShippingPolicy policy = shippingPolicyRepository
                .findFirstByVisibleTrueOrderByIdAsc().orElse(null);
        int shippingFee = policy != null ? policy.feeFor(itemsAmount) : 0;
        int discount = 0; // 쿠폰 미구현
        int total = itemsAmount + shippingFee - discount;

        // 주문 생성 (스냅샷 + 암호화)
        Order order = new Order();
        order.setMemberId(memberId);
        order.setOrdererName(req.ordererName());
        order.setOrdererPhoneEncrypted(phoneCipher.encrypt(digits(req.ordererPhone())));
        order.setOrdererEmail(blankToNull(req.ordererEmail()));
        order.setReceiverName(req.receiverName());
        order.setReceiverPhoneEncrypted(phoneCipher.encrypt(digits(req.receiverPhone())));
        order.setZipcode(req.zipcode());
        order.setAddr1(req.addr1());
        order.setAddr2(blankToNull(req.addr2()));
        order.setDeliveryMemo(blankToNull(req.deliveryMemo()));
        order.setItemsAmount(itemsAmount);
        order.setShippingFee(shippingFee);
        order.setDiscountAmount(discount);
        order.setTotalAmount(total);

        for (Line ln : lines) {
            order.addItem(new OrderItem(
                    ln.product.getId(),
                    ln.option != null ? ln.option.getId() : null,
                    ln.product.getNameKo(),
                    ln.option != null ? ln.option.getName() : null,
                    ln.product.getThumbnailKey(),
                    ln.unitPrice,
                    ln.qty));
        }

        Order saved = persistWithUniqueOrderNo(order);

        // 재고 이력. 차감 직후 잔량을 DB 에서 다시 읽어 정확히 남긴다.
        for (Line ln : lines) {
            Integer balance = (ln.option != null)
                    ? optionRepository.currentStock(ln.option.getId())
                    : productRepository.currentStock(ln.product.getId());
            stockLedgerRepository.save(new StockLedger(
                    ln.product.getId(),
                    ln.option != null ? ln.option.getId() : null,
                    -ln.qty,
                    balance != null ? balance : 0,
                    StockLedger.Reason.ORDER,
                    saved.getId()));
        }

        // 결제 레코드(READY) 생성. 실제 승인은 pay() 에서.
        paymentRepository.save(new Payment(saved.getId(), paymentGateway.provider(), total));

        // 주문에 담긴 장바구니 항목 비우기
        cartItemRepository.deleteAllById(lines.stream().map(l -> l.cartItemId).toList());

        return toView(saved);
    }

    private Order persistWithUniqueOrderNo(Order order) {
        for (int attempt = 0; attempt < ORDER_NO_RETRY; attempt++) {
            order.setOrderNo(orderNoGenerator.generate());
            try {
                return orderRepository.saveAndFlush(order);
            } catch (DataIntegrityViolationException e) {
                // 주문번호 충돌(사실상 없음). 다시 뽑아 재시도.
                if (attempt == ORDER_NO_RETRY - 1) {
                    throw e;
                }
            }
        }
        throw new IllegalStateException("주문번호 생성에 실패했습니다.");
    }

    // ── 결제(모의) ────────────────────────────────────────────

    @Transactional
    public OrderDtos.OrderView pay(String orderNo, Long memberId, OrderDtos.PayRequest req) {
        Order order = loadOwned(orderNo, memberId);
        if (!order.isPending()) {
            throw new IllegalArgumentException("이미 결제되었거나 처리된 주문입니다.");
        }
        Payment payment = paymentRepository.findByOrderId(order.getId())
                .orElseThrow(() -> new NotFoundException("결제 정보를 찾을 수 없습니다."));

        // PG 에 승인 요청. 서버가 계산한 주문 금액으로만 요청한다.
        PaymentGateway.Approval approval;
        try {
            approval = paymentGateway.approve(orderNo, order.getTotalAmount());
        } catch (PaymentGateway.PaymentException e) {
            payment.markFailed(e.getMessage());
            throw new IllegalArgumentException("결제에 실패했습니다. " + e.getMessage());
        }

        // ★ PG 승인 금액과 서버 계산 금액을 대조한다. 어긋나면 확정하지 않는다.
        if (approval.approvedAmount() != order.getTotalAmount()) {
            payment.markFailed("승인 금액 불일치: 승인 " + approval.approvedAmount()
                    + " / 주문 " + order.getTotalAmount());
            throw new IllegalStateException("결제 금액이 주문 금액과 일치하지 않습니다.");
        }

        payment.markPaid(approval.tid(), approval.method(), approval.receiptUrl());
        order.markPaid();
        return toView(order);
    }

    // ── 조회 ──────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public OrderDtos.OrderView get(String orderNo, Long memberId) {
        return toView(loadOwned(orderNo, memberId));
    }

    @Transactional(readOnly = true)
    public Page<OrderDtos.OrderSummary> listMine(Long memberId, Pageable pageable) {
        return orderRepository.findByMemberIdOrderByOrderedAtDesc(memberId, pageable)
                .map(this::toSummary);
    }

    /**
     * 주문을 불러오되 소유권을 확인한다.
     * 회원 주문이면 memberId 가 일치해야 한다. 비회원 주문은 주문번호 자체가
     * 추측 불가능한 비밀이므로 번호를 아는 사람에게만 열어준다.
     */
    private Order loadOwned(String orderNo, Long memberId) {
        Order order = orderRepository.findByOrderNo(orderNo)
                .orElseThrow(() -> new NotFoundException("주문을 찾을 수 없습니다."));
        if (order.getMemberId() != null) {
            if (memberId == null || !order.getMemberId().equals(memberId)) {
                // 남의 회원 주문. 존재 여부를 숨기려 404 로 응답한다.
                throw new NotFoundException("주문을 찾을 수 없습니다.");
            }
        }
        return order;
    }

    // ── 매핑 ──────────────────────────────────────────────────

    private OrderDtos.OrderView toView(Order o) {
        List<OrderDtos.ItemView> items = o.getItems().stream()
                .map(it -> new OrderDtos.ItemView(
                        it.getProductId(),
                        null, // 슬러그는 상세 링크용. 지금은 스냅샷만으로 충분하므로 생략.
                        it.getProductNameSnapshot(),
                        it.getOptionNameSnapshot(),
                        thumbUrl(it.getThumbnailKeySnapshot()),
                        it.getUnitPriceSnapshot(),
                        it.getQuantity(),
                        it.getLineAmount()))
                .toList();

        return new OrderDtos.OrderView(
                o.getOrderNo(),
                o.getStatus(),
                o.getOrdererName(),
                maskPhone(decryptSafe(o.getOrdererPhoneEncrypted())),
                o.getOrdererEmail(),
                o.getReceiverName(),
                maskPhone(decryptSafe(o.getReceiverPhoneEncrypted())),
                o.getZipcode(),
                o.getAddr1(),
                o.getAddr2(),
                o.getDeliveryMemo(),
                o.getItemsAmount(),
                o.getShippingFee(),
                o.getDiscountAmount(),
                o.getTotalAmount(),
                o.getOrderedAt(),
                o.getPaidAt(),
                items);
    }

    private OrderDtos.OrderSummary toSummary(Order o) {
        List<OrderItem> its = o.getItems();
        String title = its.isEmpty() ? "주문" : its.get(0).getProductNameSnapshot();
        if (its.size() > 1) {
            title += " 외 " + (its.size() - 1) + "건";
        }
        String thumb = its.isEmpty() ? null : thumbUrl(its.get(0).getThumbnailKeySnapshot());
        return new OrderDtos.OrderSummary(
                o.getOrderNo(), o.getStatus(), title, its.size(),
                o.getTotalAmount(), thumb, o.getOrderedAt());
    }

    // ── 내부 헬퍼 ─────────────────────────────────────────────

    private record Line(Product product, ProductOption option, int unitPrice, int qty,
                        Long cartItemId) {
    }

    private int effectiveUnitPrice(Product product, ProductOption option) {
        int base = product.getDiscountPrice() != null
                ? product.getDiscountPrice() : product.getPrice();
        int delta = option != null ? option.getPriceDelta() : 0;
        return base + delta;
    }

    private String thumbUrl(String key) {
        if (key == null || key.isBlank()) {
            return null;
        }
        return imageService.urlOf(key + "_thumb.webp");
    }

    /** 암호화된 폰을 복호화해 숫자만 돌려준다(마스킹 전용). 실패하면 null. */
    private String decryptSafe(String encrypted) {
        if (encrypted == null) {
            return null;
        }
        try {
            return digits(phoneCipher.decrypt(encrypted));
        } catch (Exception e) {
            return null;
        }
    }

    private String digits(String phone) {
        return phone == null ? null : phone.replaceAll("\\D", "");
    }

    private String maskPhone(String digits) {
        if (digits == null || digits.length() < 7) {
            return null;
        }
        String head = digits.substring(0, 3);
        String tail = digits.substring(digits.length() - 4);
        return head + "-****-" + tail;
    }

    private String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s;
    }
}
