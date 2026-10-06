package com.rizenfood.api.notify;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import jakarta.annotation.PreDestroy;

@Component
class OrderNotifier {
    private static final Logger log = LoggerFactory.getLogger(OrderNotifier.class);
    private final AlimtalkProperties properties;
    private final OrderNotificationBuilder builder;
    private final AlimtalkSender sender;
    private final ThreadPoolExecutor executor = new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<>(1000), r -> {
        Thread t = new Thread(r, "alimtalk");
        t.setDaemon(true);
        return t;
    });
    OrderNotifier(AlimtalkProperties properties, OrderNotificationBuilder builder, AlimtalkSender sender){
        this.properties = properties;
        this.builder = builder;
        this.sender = sender;
    }
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(OrderNotificationEvent event){
        if(!properties.enabled()){
            return;
        }try{
            executor.execute(()-> deliver(event));
        }catch (RejectedExecutionException e){
            log.warn("알림톡 대기열이 가득 차 보내지 못했다: 주문 {} {}", event.orderId(), event.type());
        }
    }
    private void deliver(OrderNotificationEvent event){
        try{
            builder.build(event).ifPresent(sender::send);
        }catch(RuntimeException e){
            log.warn("알림톡 발송 실패: 주문 {} {} - {}", event.orderId(), event.type(), e.getMessage());
        }
    }
    @PreDestroy
    void shutdown() throws InterruptedException{
        executor.shutdown();
        executor.awaitTermination(10, TimeUnit.SECONDS);
    }
}
