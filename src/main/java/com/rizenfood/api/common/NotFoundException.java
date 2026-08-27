package com.rizenfood.api.common;

/**
 * 요청한 것이 없을 때. 404 로 나간다.
 *
 * 숨긴 상품처럼 "있지만 보여줄 수 없는" 경우에도 이것을 쓴다.
 * 403 을 주면 그 주소에 무언가 존재한다는 사실을 알려주는 셈이다.
 */
public class NotFoundException extends RuntimeException {

    public NotFoundException(String message) {
        super(message);
    }
}
