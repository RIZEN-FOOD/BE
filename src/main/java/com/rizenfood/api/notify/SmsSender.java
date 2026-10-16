package com.rizenfood.api.notify;

/** 문자(SMS·LMS) 발송 — 운영자 알림용 (2026-10-16). */
public interface SmsSender {

    /**
     * @param to   받을 번호(숫자만)
     * @param text 본문. 90바이트를 넘으면 장문(LMS)으로 나간다
     * @throws AlimtalkSender.AlimtalkException 업체가 거절했거나 설정이 비었을 때
     */
    void send(String to, String text);
}
