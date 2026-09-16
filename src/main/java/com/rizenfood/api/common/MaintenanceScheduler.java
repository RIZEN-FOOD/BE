package com.rizenfood.api.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 오래된 데이터 정리. 새벽에 하루 한 번 돈다.
 *
 * 실제 작업은 MaintenanceTasks 가 한다 (트랜잭션이 걸리게 하려면 다른 빈이어야 한다).
 * 여기서는 순서를 정하고, 한 건이 실패해도 나머지는 돌게 한다.
 *
 * 결과는 지운 게 없어도 한 줄 남긴다 — 로그가 조용하면 "잘 돌았다"인지
 * "돌다가 죽었다"인지 알 수 없기 때문이다.
 */
@Component
public class MaintenanceScheduler {

    private static final Logger log = LoggerFactory.getLogger(MaintenanceScheduler.class);

    private final MaintenanceTasks tasks;

    public MaintenanceScheduler(MaintenanceTasks tasks) {
        this.tasks = tasks;
    }

    @Scheduled(cron = "0 30 4 * * *", zone = "Asia/Seoul")
    public void cleanUp() {
        int carts = run("오래된 비회원 장바구니", tasks::deleteStaleGuestCarts);
        run("만료된 로그인 토큰", () -> {
            tasks.deleteExpiredRefreshTokens();
            return -1; // 지운 건수를 돌려주지 않는 쿼리다
        });
        int members = run("보존기간이 지난 탈퇴 회원", tasks::purgeWithdrawnMembers);
        log.info("정리 완료 — 장바구니 {}건, 탈퇴 회원 {}건", Math.max(carts, 0), Math.max(members, 0));
    }

    /** 실패해도 서비스는 계속 떠 있어야 한다. 다만 조용히 넘기지 않고 오류로 남긴다. */
    private int run(String what, java.util.function.IntSupplier job) {
        try {
            int affected = job.getAsInt();
            if (affected > 0) {
                log.info("정리: {} {}건", what, affected);
            }
            return affected;
        } catch (Exception e) {
            log.error("정리 실패: {}", what, e);
            return -1;
        }
    }
}
