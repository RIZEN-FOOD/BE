package com.rizenfood.api.image;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Iterator;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;

import org.springframework.stereotype.Component;

/**
 * 업로드된 파일이 정말 이미지인지 확인한다.
 *
 * 세 겹으로 본다. 앞의 두 개는 클라이언트가 조작할 수 있어서 그것만으로는 부족하다.
 *   1. 확장자      — 화이트리스트 (jpg/jpeg/png/webp)
 *   2. Content-Type — 확장자와 앞뒤가 맞는지
 *   3. 시그니처     — 파일 앞부분의 매직넘버. 실제 형식은 이것만 믿는다.
 *
 * 여기를 통과해도 안전하다고 보지 않는다.
 * 이미지 안에 코드를 숨기는 수법이 있어서, 통과한 뒤에도 반드시 재인코딩한다
 * (ImageProcessor 참조).
 */
@Component
public class ImageValidator {

    private final ImageProperties properties;

    public ImageValidator(ImageProperties properties) {
        this.properties = properties;
    }

    /**
     * @param bytes        업로드된 원본 바이트
     * @param originalName 사용자가 올린 파일명 (확장자 확인용. 저장에는 쓰지 않는다)
     * @param declaredType 요청의 Content-Type
     * @return 시그니처로 판별한 실제 형식
     */
    public ImageFormat validate(byte[] bytes, String originalName, String declaredType) {
        if (bytes == null || bytes.length == 0) {
            throw new ImageValidationException("파일이 비어 있습니다. 다시 선택해 주세요.");
        }

        if (bytes.length > properties.maxFileSizeBytes()) {
            long limitMb = properties.maxFileSizeBytes() / (1024 * 1024);
            throw new ImageValidationException(
                    "파일이 너무 큽니다. " + limitMb + "MB 이하로 올려 주세요.");
        }

        // 1. 확장자 화이트리스트
        ImageFormat byExtension = ImageFormat.byExtension(extensionOf(originalName))
                .orElseThrow(() -> new ImageValidationException(
                        "JPG, PNG, WEBP 파일만 올릴 수 있습니다."));

        // 3. 시그니처. 확장자·Content-Type 과 어긋나면 위장한 파일이다.
        ImageFormat bySignature = ImageFormat.bySignature(bytes)
                .orElseThrow(() -> new ImageValidationException(
                        "이미지 파일이 아닙니다. 파일이 손상됐거나 확장자만 바뀐 파일일 수 있습니다."));

        if (byExtension != bySignature) {
            throw new ImageValidationException(
                    "파일 확장자와 실제 형식이 다릅니다. 원본 파일을 그대로 올려 주세요.");
        }

        // 2. Content-Type. 비어 있으면 넘어가되, 값이 있으면 일치해야 한다.
        if (declaredType != null && !declaredType.isBlank()) {
            String normalized = declaredType.split(";")[0].trim();
            if (!normalized.equalsIgnoreCase(bySignature.mimeType())) {
                throw new ImageValidationException(
                        "파일 형식 정보가 실제 파일과 다릅니다. 원본 파일을 그대로 올려 주세요.");
            }
        }

        // 실제로 디코딩 가능한지, 그리고 크기가 감당할 수 있는 범위인지 본다.
        assertDecodableAndBounded(bytes);

        return bySignature;
    }

    /**
     * 헤더만 읽어 픽셀 수를 먼저 확인한다.
     *
     * 전체를 디코딩한 뒤에 크기를 재면 이미 메모리를 다 쓴 뒤다.
     * 파일은 몇 KB 인데 압축을 풀면 수억 픽셀이 되는 파일(압축 폭탄)이 있어서
     * 디코딩 전에 걸러야 한다.
     */
    private void assertDecodableAndBounded(byte[] bytes) {
        try (ImageInputStream stream = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            if (stream == null) {
                throw new ImageValidationException("이미지를 읽을 수 없습니다.");
            }
            Iterator<ImageReader> readers = ImageIO.getImageReaders(stream);
            if (!readers.hasNext()) {
                throw new ImageValidationException("지원하지 않는 이미지 형식입니다.");
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(stream);
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);

                if (width <= 0 || height <= 0) {
                    throw new ImageValidationException("이미지 크기를 확인할 수 없습니다.");
                }
                if ((long) width * height > properties.maxPixels()) {
                    throw new ImageValidationException(
                            "이미지 해상도가 너무 큽니다. 크기를 줄여서 올려 주세요.");
                }

                // 헤더가 멀쩡해도 픽셀 데이터가 깨진 경우가 있어 실제로 한 번 읽어본다.
                BufferedImage image = reader.read(0);
                if (image == null) {
                    throw new ImageValidationException("이미지를 읽을 수 없습니다. 파일이 손상된 것 같습니다.");
                }
            } finally {
                reader.dispose();
            }
        } catch (IOException e) {
            throw new ImageValidationException("이미지를 읽을 수 없습니다. 파일이 손상된 것 같습니다.");
        }
    }

    private String extensionOf(String filename) {
        if (filename == null) {
            return null;
        }
        int dot = filename.lastIndexOf('.');
        return (dot < 0 || dot == filename.length() - 1) ? null : filename.substring(dot + 1);
    }
}
