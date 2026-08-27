package com.rizenfood.api.image.storage;

/**
 * 처리된 이미지를 어디에 둘지에 대한 추상화.
 *
 * 운영은 S3 를 쓴다. 앱 서버가 파일을 직접 서빙하지 않는다 (기획서 §10).
 * 서빙까지 앱이 맡으면 경로 조작으로 서버 파일을 읽히거나,
 * 업로드된 파일이 우리 도메인에서 실행되는 문제가 생긴다.
 *
 * 로컬 개발에서는 S3 계정 없이도 돌아가야 하므로 디렉터리 구현으로 대체한다.
 */
public interface ImageStorage {

    /**
     * @param key         저장 키 (예: products/2026/08/uuid_thumb.webp)
     * @param bytes       저장할 바이트
     * @param contentType 서빙 시 내려줄 Content-Type
     * @return 공개 URL
     */
    String put(String key, byte[] bytes, String contentType);

    /** 저장된 파일을 지운다. 없으면 조용히 넘어간다. */
    void delete(String key);

    /** 키에 대응하는 공개 URL */
    String urlOf(String key);
}
