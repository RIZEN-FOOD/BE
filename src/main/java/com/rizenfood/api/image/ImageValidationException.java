package com.rizenfood.api.image;

/**
 * 업로드된 파일이 이미지로 받아들일 수 없을 때 던진다.
 *
 * 메시지는 사용자에게 그대로 보여줄 수 있는 문장으로 쓴다.
 * 관리자 화면을 쓰는 사람은 개발자가 아니므로 "무엇이 잘못됐고 어떻게 하면 되는지"가
 * 문장 안에 있어야 한다.
 */
public class ImageValidationException extends RuntimeException {

    public ImageValidationException(String message) {
        super(message);
    }
}
