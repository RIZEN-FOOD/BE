package com.rizenfood.api.notify;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import jakarta.annotation.PreDestroy;

/**
 * 운영자 알림 문자 (2026-10-16).
 *
 * 새 주문(결제 완료)·취소·반품·교환 요청을 운영자 휴대폰으로 알린다. 전에는 이런 알림이 하나도 없어서
 * 관리자 화면을 열어 봐야만 알 수 있었다.
 *
 * ★ 커밋된 뒤에만 보낸다(롤백된 일은 알리지 않는다). 손님 알림톡과 따로 돈다.
 * ★ 문자 업체가 느리거나 실패해도 주문·요청 처리는 영향받지 않는다 — 별도 스레드에서 보내고 실패는 로그만 남긴다.
 */
@Component
class AdminNotifier {

    private static final Logger log = LoggerFactory.getLogger(AdminNotifier.class);

    private final AdminNotifyProperties properties;
    private final AdminNotificationBuilder builder;
    private final SmsSender sender;
    private final ThreadPoolExecutor executor = new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<>(500), r -> {
                Thread t = new Thread(r, "admin-notify");
                t.setDaemon(true);
                return t;
            });

    AdminNotifier(AdminNotifyProperties properties, AdminNotificationBuilder builder, SmsSender sender) {
        this.properties = properties;
        this.builder = builder;
        this.sender = sender;
    }

    /** 결제가 확정되면 — 손님 «결제 완료» 알림톡과 같은 사건을 듣는다. */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOrder(OrderNotificationEvent event) {
        if (event.type() == OrderNotificationEvent.Type.PAID) {
            enqueue("새 주문 " + event.orderId(), () -> builder.newOrder(event.orderId()));
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onClaim(ClaimRequestedEvent event) {
        enqueue(event.type() + " 요청 " + event.orderId(), () -> builder.claimRequested(event.orderId(), event.type()));
    }

    private void enqueue(String what, Supplier<Optional<String>> text) {
        List<String> phones = properties.phoneList();
        if (!properties.enabled() || phones.isEmpty()) {
            return;
        }
        try {
            executor.execute(() -> deliver(what, text, phones));
        } catch (RejectedExecutionException e) {
            log.warn("운영자 알림 대기열이 가득 차 보내지 못했다: {}", what);
        }
    }

    private void deliver(String what, Supplier<Optional<String>> text, List<String> phones) {
        Optional<String> body;
        try {
            body = text.get();
        } catch (RuntimeException e) {
            log.warn("운영자 알림 본문을 만들지 못했다: {} - {}", what, e.getMessage());
            return;
        }
        body.ifPresent(t -> {
            for (String to : phones) {
                try {
                    sender.send(to, t);
                } catch (RuntimeException e) {
                    log.warn("운영자 알림 문자 실패: {} → {}****{} - {}", what,
                            to.substring(0, 3), to.substring(to.length() - 2), e.getMessage());
                }
            }
        });
    }

    @PreDestroy
    void shutdown() throws InterruptedException {
        executor.shutdown();
        executor.awaitTermination(10, TimeUnit.SECONDS);
    }
}
