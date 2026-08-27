package com.rizenfood.api.image;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

/**
 * 업로드를 허용하는 이미지 형식.
 *
 * 확장자와 Content-Type 은 클라이언트가 마음대로 보낼 수 있는 값이다.
 * 그래서 파일 앞부분의 시그니처(매직넘버)까지 봐야 실제 형식을 알 수 있다.
 */
public enum ImageFormat {

    JPEG("image/jpeg", new String[] {"jpg", "jpeg"}, new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF}),
    PNG("image/png", new String[] {"png"},
            new byte[] {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A}),
    WEBP("image/webp", new String[] {"webp"}, null); // RIFF....WEBP 는 따로 검사한다

    private final String mimeType;
    private final String[] extensions;
    private final byte[] signature;

    ImageFormat(String mimeType, String[] extensions, byte[] signature) {
        this.mimeType = mimeType;
        this.extensions = extensions;
        this.signature = signature;
    }

    public String mimeType() {
        return mimeType;
    }

    public static Optional<ImageFormat> byExtension(String extension) {
        if (extension == null) {
            return Optional.empty();
        }
        String normalized = extension.toLowerCase(Locale.ROOT);
        return Arrays.stream(values())
                .filter(f -> Arrays.asList(f.extensions).contains(normalized))
                .findFirst();
    }

    /**
     * 파일 내용의 앞부분을 보고 실제 형식을 알아낸다.
     * 여기서 나온 값만 신뢰한다.
     */
    public static Optional<ImageFormat> bySignature(byte[] bytes) {
        if (bytes == null || bytes.length < 12) {
            return Optional.empty();
        }
        for (ImageFormat format : values()) {
            if (format == WEBP) {
                // WebP 는 RIFF 컨테이너다. 0~3 이 "RIFF", 8~11 이 "WEBP".
                if (matchesAscii(bytes, 0, "RIFF") && matchesAscii(bytes, 8, "WEBP")) {
                    return Optional.of(WEBP);
                }
                continue;
            }
            if (startsWith(bytes, format.signature)) {
                return Optional.of(format);
            }
        }
        return Optional.empty();
    }

    private static boolean startsWith(byte[] bytes, byte[] prefix) {
        if (bytes.length < prefix.length) {
            return false;
        }
        for (int i = 0; i < prefix.length; i++) {
            if (bytes[i] != prefix[i]) {
                return false;
            }
        }
        return true;
    }

    private static boolean matchesAscii(byte[] bytes, int offset, String expected) {
        if (bytes.length < offset + expected.length()) {
            return false;
        }
        for (int i = 0; i < expected.length(); i++) {
            if (bytes[offset + i] != (byte) expected.charAt(i)) {
                return false;
            }
        }
        return true;
    }
}
