package com.rizenfood.api.image;

/**
 * 저장하는 이미지 크기 3종.
 *
 * 목록에 원본을 뿌리면 모바일에서 수 MB 를 내려받게 된다.
 * 쓰이는 자리에 맞는 크기를 미리 만들어둔다.
 */
public enum ImageVariant {

    /** 목록·장바구니 썸네일 */
    THUMBNAIL("thumb", 400),
    /** 상세 갤러리 */
    MEDIUM("medium", 1000),
    /** 확대(줌) 용. 이보다 큰 원본은 여기까지 줄인다. */
    LARGE("large", 2000);

    private final String suffix;
    private final int maxEdge;

    ImageVariant(String suffix, int maxEdge) {
        this.suffix = suffix;
        this.maxEdge = maxEdge;
    }

    public String suffix() {
        return suffix;
    }

    /** 가로·세로 중 긴 변의 최대 길이. 비율은 유지한다. */
    public int maxEdge() {
        return maxEdge;
    }
}
