package com.rizenfood.api.image;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.Test;

/**
 * WebP 인코딩이 이 실행 환경에서 실제로 되는지 확인한다.
 *
 * webp-imageio 는 네이티브 라이브러리를 쓰기 때문에 OS·아키텍처에 따라
 * 로드에 실패할 수 있다. 배포 대상(EC2 Linux)에서도 이 테스트가 통과해야 한다.
 * 실패하면 컨테이너에 cwebp 바이너리를 넣고 프로세스로 호출하는 방식으로 바꾼다.
 */
class WebpAvailabilityTest {

    @Test
    void webp_인코더가_등록되어_있다() {
        List<String> writers = new ArrayList<>();
        ImageIO.getImageWritersByFormatName("webp").forEachRemaining(w -> writers.add(w.getClass().getName()));

        assertThat(writers)
                .as("webp ImageWriter 가 하나도 없다. 네이티브 로딩에 실패했을 가능성이 크다.")
                .isNotEmpty();
    }

    @Test
    void webp_로_쓰고_다시_읽을_수_있다() throws Exception {
        BufferedImage source = new BufferedImage(240, 160, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = source.createGraphics();
        g.setColor(new Color(0xDE, 0xB1, 0x91)); // 브랜드 클레이
        g.fillRect(0, 0, 240, 160);
        g.setColor(new Color(0x35, 0x40, 0x6B)); // 블루베리
        g.fillOval(60, 40, 120, 80);
        g.dispose();

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        boolean written = ImageIO.write(source, "webp", out);
        assertThat(written).as("webp 로 쓰기에 실패했다").isTrue();

        byte[] bytes = out.toByteArray();
        assertThat(bytes.length).isGreaterThan(0);

        // WebP 시그니처: RIFF....WEBP
        assertThat(new String(bytes, 0, 4)).isEqualTo("RIFF");
        assertThat(new String(bytes, 8, 4)).isEqualTo("WEBP");

        BufferedImage roundTrip = ImageIO.read(new ByteArrayInputStream(bytes));
        assertThat(roundTrip).as("쓴 webp 를 다시 읽지 못했다").isNotNull();
        assertThat(roundTrip.getWidth()).isEqualTo(240);
        assertThat(roundTrip.getHeight()).isEqualTo(160);
    }
}
