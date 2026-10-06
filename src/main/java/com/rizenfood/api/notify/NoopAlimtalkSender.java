package com.rizenfood.api.notify;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.alimtalk.provider", havingValue = "none", matchIfMissing = true)
class NoopAlimtalkSender implements AlimtalkSender {

    private static final Logger log = LoggerFactory.getLogger(NoopAlimtalkSender.class);

    @Override
    public String provider() {
        return "none";
    }
    @Override
    public void send(AlimtalkMessage message) {
        log.info("알림톡(발송 업체 미설정 — 보내지 않음): {} → {}", message.template(), message.masked());
    }
}