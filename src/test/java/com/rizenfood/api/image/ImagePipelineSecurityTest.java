package com.rizenfood.api.image;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 파이프라인이 실제로 막아준다고 주장하는 것들을 하나씩 확인한다.
 *
 * 여기 있는 테스트가 깨지면 업로드 경로에 구멍이 뚫린 것이다.
 */
class ImagePipelineSecurityTest {

    private static final ImageProperties PROPS = new ImageProperties(10L * 1024 * 1024, 50_000_000L, 0.82f);

    private final ImageValidator validator = new ImageValidator(PROPS);
    private final ImageProcessor processor = new ImageProcessor(PROPS);

    /** 브랜드 색으로 채운 진짜 이미지 한 장 */
    private static byte[] realImage(String format, int w, int h) throws Exception {
        BufferedImage image = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setColor(new Color(0xDE, 0xB1, 0x91));
        g.fillRect(0, 0, w, h);
        g.setColor(new Color(0x35, 0x40, 0x6B));
        g.fillOval(w / 4, h / 4, w / 2, h / 2);
        g.dispose();

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, format, out);
        return out.toByteArray();
    }

    private static byte[] concat(byte[] a, byte[] b) {
        byte[] merged = new byte[a.length + b.length];
        System.arraycopy(a, 0, merged, 0, a.length);
        System.arraycopy(b, 0, merged, a.length, b.length);
        return merged;
    }

    // ── 1. 재인코딩 ──────────────────────────────────────────

    @Test
    @DisplayName("이미지 뒤에 붙인 코드가 재인코딩 후 사라진다")
    void 재인코딩이_붙어있는_페이로드를_제거한다() throws Exception {
        String payload = "<script>alert(1)</script><?php system($_GET['c']); ?>";
        byte[] poisoned = concat(realImage("png", 300, 200), payload.getBytes(StandardCharsets.UTF_8));

        // 원본에는 분명히 들어 있다
        assertThat(new String(poisoned, StandardCharsets.ISO_8859_1)).contains(payload);
        // 그리고 검증은 통과한다. 정상 PNG 헤더를 가졌기 때문이다.
        validator.validate(poisoned, "innocent.png", "image/png");

        ImageProcessor.Result result = processor.process(poisoned);

        // 재인코딩 결과에는 어느 크기에도 남아 있지 않다
        for (ImageVariant variant : ImageVariant.values()) {
            String content = new String(result.variants().get(variant), StandardCharsets.ISO_8859_1);
            assertThat(content)
                    .as("%s 에 페이로드가 남아 있다", variant)
                    .doesNotContain("<script>")
                    .doesNotContain("<?php")
                    .doesNotContain("system(");
        }
    }

    // ── 2. 위장 파일 ─────────────────────────────────────────

    @Test
    @DisplayName("확장자만 이미지인 텍스트 파일은 거부된다")
    void 확장자만_바꾼_파일은_거부된다() {
        byte[] notAnImage = "#!/bin/sh\nrm -rf /\n".getBytes(StandardCharsets.UTF_8);

        assertThatThrownBy(() -> validator.validate(notAnImage, "payload.jpg", "image/jpeg"))
                .isInstanceOf(ImageValidationException.class)
                .hasMessageContaining("이미지 파일이 아닙니다");
    }

    @Test
    @DisplayName("확장자와 실제 형식이 다르면 거부된다")
    void 확장자와_시그니처가_어긋나면_거부된다() throws Exception {
        byte[] actuallyPng = realImage("png", 100, 100);

        assertThatThrownBy(() -> validator.validate(actuallyPng, "photo.jpg", "image/jpeg"))
                .isInstanceOf(ImageValidationException.class)
                .hasMessageContaining("확장자와 실제 형식이 다릅니다");
    }

    @Test
    @DisplayName("Content-Type 이 실제 파일과 다르면 거부된다")
    void 선언된_타입이_다르면_거부된다() throws Exception {
        byte[] png = realImage("png", 100, 100);

        assertThatThrownBy(() -> validator.validate(png, "photo.png", "image/gif"))
                .isInstanceOf(ImageValidationException.class)
                .hasMessageContaining("파일 형식 정보가 실제 파일과 다릅니다");
    }

    @Test
    @DisplayName("허용 목록에 없는 확장자는 거부된다")
    void 허용되지_않은_확장자는_거부된다() throws Exception {
        byte[] png = realImage("png", 100, 100);

        assertThatThrownBy(() -> validator.validate(png, "photo.svg", "image/svg+xml"))
                .isInstanceOf(ImageValidationException.class)
                .hasMessageContaining("JPG, PNG, WEBP");
    }

    @Test
    @DisplayName("빈 파일은 거부된다")
    void 빈_파일은_거부된다() {
        assertThatThrownBy(() -> validator.validate(new byte[0], "empty.png", "image/png"))
                .isInstanceOf(ImageValidationException.class);
    }

    // ── 3. 리사이즈 · 변환 ───────────────────────────────────

    @Test
    @DisplayName("크기 3종이 만들어지고 모두 WebP 다")
    void 크기_3종이_webp_로_만들어진다() throws Exception {
        byte[] big = realImage("jpeg", 3000, 2000);
        validator.validate(big, "product.jpg", "image/jpeg");

        ImageProcessor.Result result = processor.process(big);

        assertThat(result.variants()).hasSize(ImageVariant.values().length);
        for (ImageVariant variant : ImageVariant.values()) {
            byte[] bytes = result.variants().get(variant);
            assertThat(bytes).as("%s 가 비었다", variant).isNotEmpty();

            // WebP 시그니처
            assertThat(new String(bytes, 0, 4, StandardCharsets.ISO_8859_1)).isEqualTo("RIFF");
            assertThat(new String(bytes, 8, 4, StandardCharsets.ISO_8859_1)).isEqualTo("WEBP");

            BufferedImage decoded = ImageIO.read(new java.io.ByteArrayInputStream(bytes));
            int longEdge = Math.max(decoded.getWidth(), decoded.getHeight());
            assertThat(longEdge)
                    .as("%s 의 긴 변이 상한을 넘었다", variant)
                    .isLessThanOrEqualTo(variant.maxEdge());
        }

        // 썸네일이 큰 것보다 확실히 작아야 한다
        assertThat(result.variants().get(ImageVariant.THUMBNAIL).length)
                .isLessThan(result.variants().get(ImageVariant.LARGE).length);
    }

    @Test
    @DisplayName("원본보다 크게 늘리지 않는다")
    void 작은_이미지를_억지로_키우지_않는다() throws Exception {
        byte[] small = realImage("png", 200, 150);
        validator.validate(small, "small.png", "image/png");

        ImageProcessor.Result result = processor.process(small);

        BufferedImage large = ImageIO.read(
                new java.io.ByteArrayInputStream(result.variants().get(ImageVariant.LARGE)));
        assertThat(large.getWidth()).isEqualTo(200);
        assertThat(large.getHeight()).isEqualTo(150);
    }
}
