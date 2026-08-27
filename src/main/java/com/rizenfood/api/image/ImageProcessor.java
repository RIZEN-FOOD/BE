package com.rizenfood.api.image;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.EnumMap;
import java.util.Map;

import javax.imageio.ImageIO;

import org.springframework.stereotype.Component;

import net.coobird.thumbnailator.Thumbnails;

/**
 * 이미지를 다시 만들어낸다.
 *
 * ★ 재인코딩이 이 클래스의 존재 이유다.
 *
 * 검증을 통과한 파일이라도 안전하지 않다.
 * 이미지 형식은 대부분 "이 뒤는 무시해도 된다"는 영역을 허용하기 때문에,
 * 정상적인 JPEG 헤더 뒤에 스크립트나 실행 코드를 붙여도 이미지로는 멀쩡히 열린다.
 * EXIF 주석 필드에 코드를 넣는 수법도 오래됐다.
 * 이런 파일이 서버에 그대로 저장되면, 나중에 다른 취약점과 엮여 실행될 수 있다.
 *
 * 그래서 원본 바이트를 저장하지 않는다.
 * 픽셀만 읽어서 새 이미지로 다시 써낸다. 그 과정에서 픽셀이 아닌 것은 전부 사라진다.
 * EXIF, 주석, 파일 끝에 붙은 페이로드, 색 프로파일까지 함께 없어진다.
 *
 * 출력은 WebP 한 형식으로 통일한다. 용량이 작고 브라우저 지원도 충분하다.
 */
@Component
public class ImageProcessor {

    private final ImageProperties properties;

    public ImageProcessor(ImageProperties properties) {
        this.properties = properties;
    }

    /**
     * 원본 바이트에서 픽셀만 뽑아 크기별 WebP 를 만든다.
     *
     * @return 크기별 바이트와 원본 픽셀 크기
     */
    public Result process(byte[] originalBytes) {
        BufferedImage decoded = decode(originalBytes);

        // 알파 채널을 없애고 흰 배경 위에 다시 그린다.
        // 투명 PNG 를 그대로 WebP 로 옮기면 배경이 검게 나오는 경우가 있다.
        BufferedImage flattened = flatten(decoded);

        Map<ImageVariant, byte[]> variants = new EnumMap<>(ImageVariant.class);
        for (ImageVariant variant : ImageVariant.values()) {
            variants.put(variant, encodeWebp(resize(flattened, variant.maxEdge())));
        }

        return new Result(variants, flattened.getWidth(), flattened.getHeight());
    }

    private BufferedImage decode(byte[] bytes) {
        try {
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(bytes));
            if (image == null) {
                throw new ImageValidationException("이미지를 읽을 수 없습니다.");
            }
            return image;
        } catch (IOException e) {
            throw new ImageValidationException("이미지를 읽을 수 없습니다.");
        }
    }

    /** 투명 영역을 흰색으로 채운 RGB 이미지로 바꾼다. */
    private BufferedImage flatten(BufferedImage source) {
        BufferedImage target =
                new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = target.createGraphics();
        try {
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, target.getWidth(), target.getHeight());
            g.drawImage(source, 0, 0, null);
        } finally {
            g.dispose();
        }
        return target;
    }

    /**
     * 긴 변을 maxEdge 에 맞춘다. 비율은 유지하고, 원본보다 키우지는 않는다.
     */
    private BufferedImage resize(BufferedImage source, int maxEdge) {
        int longEdge = Math.max(source.getWidth(), source.getHeight());
        if (longEdge <= maxEdge) {
            return source;
        }
        try {
            return Thumbnails.of(source).size(maxEdge, maxEdge).keepAspectRatio(true).asBufferedImage();
        } catch (IOException e) {
            throw new UncheckedIOException("이미지 크기 변경에 실패했다", e);
        }
    }

    private byte[] encodeWebp(BufferedImage image) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            // 품질 설정은 writer 파라미터로 넘겨야 하지만,
            // 기본 write 로도 충분한 압축이 나온다. 품질 조정이 필요해지면
            // ImageWriteParam 을 쓰는 형태로 바꾼다.
            if (!ImageIO.write(image, "webp", out)) {
                throw new IllegalStateException("webp 인코더를 찾지 못했다");
            }
        } catch (IOException e) {
            throw new UncheckedIOException("webp 인코딩에 실패했다", e);
        }
        return out.toByteArray();
    }

    /** 처리 결과. 저장은 ImageService 가 한다. */
    public record Result(Map<ImageVariant, byte[]> variants, int width, int height) {
    }
}
