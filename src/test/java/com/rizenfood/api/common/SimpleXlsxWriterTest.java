package com.rizenfood.api.common;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 출고용 엑셀 파일 형식. */
class SimpleXlsxWriterTest {

    private Map<String, String> unzip(byte[] xlsx) throws IOException {
        Map<String, String> files = new HashMap<>();
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(xlsx), StandardCharsets.UTF_8)) {
            ZipEntry e;
            while ((e = zip.getNextEntry()) != null) {
                files.put(e.getName(), new String(zip.readAllBytes(), StandardCharsets.UTF_8));
            }
        }
        return files;
    }

    @Test
    @DisplayName("xlsx 필수 구성 파일이 모두 들어 있다")
    void hasAllParts() throws IOException {
        byte[] file = SimpleXlsxWriter.write("주문", List.of("A"), List.of(List.of("x")), null);
        assertThat(unzip(file)).containsKeys("[Content_Types].xml", "_rels/.rels", "xl/workbook.xml",
                "xl/_rels/workbook.xml.rels", "xl/styles.xml", "xl/worksheets/sheet1.xml");
    }

    @Test
    @DisplayName("우편번호 앞자리 0 은 문자열로 남고, 수식처럼 보이는 값도 수식이 되지 않는다")
    void textCellsStayText() throws IOException {
        byte[] file = SimpleXlsxWriter.write("주문", List.of("우편번호", "메모", "수량"),
                List.of(Arrays.asList("06234", "=HYPERLINK(\"http://x\")", 3)), new int[] {8, 20, 6});
        String sheet = unzip(file).get("xl/worksheets/sheet1.xml");

        assertThat(sheet).contains("<t xml:space=\"preserve\">06234</t>");
        assertThat(sheet).contains("=HYPERLINK(&quot;http://x&quot;)");
        assertThat(sheet).doesNotContain("<f>");
        assertThat(sheet).contains("<c r=\"C2\"><v>3</v></c>");
    }

    @Test
    @DisplayName("XML 특수문자는 바꾸고 제어문자는 뺀다, null 칸은 비운다")
    void escapesAndSkips() throws IOException {
        byte[] file = SimpleXlsxWriter.write("주문", List.of("a", "b", "c"),
                List.of(Arrays.asList("A&B <b>", null, "xy\n")), null);
        String sheet = unzip(file).get("xl/worksheets/sheet1.xml");

        assertThat(sheet).contains("A&amp;B &lt;b&gt;");
        assertThat(sheet).doesNotContain("B2");
        assertThat(sheet).contains(">xy\n</t>");
    }

    @Test
    @DisplayName("열 이름: A, Z, AA")
    void columnNames() {
        assertThat(SimpleXlsxWriter.columnName(0)).isEqualTo("A");
        assertThat(SimpleXlsxWriter.columnName(25)).isEqualTo("Z");
        assertThat(SimpleXlsxWriter.columnName(26)).isEqualTo("AA");
    }
}
