package com.rizenfood.api.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.rizenfood.api.common.SimpleXlsxWriter;

/**
 * 출고 대행사가 송장을 채워 보낸 엑셀 읽기.
 *
 * 대행사는 우리 파일을 엑셀로 열어 저장해서 보낸다. 그때 글자가 공유 문자열(sharedStrings)로
 * 바뀌고 칸 순서도 바뀔 수 있어서, 그 상태로도 읽히는지가 이 테스트의 핵심이다.
 */
class OrderTrackingSheetTest {

    @Test
    @DisplayName("우리가 내보낸 양식 그대로 읽는다")
    void readsOurOwnSheet() {
        byte[] file = SimpleXlsxWriter.write("주문",
                OrderShippingSheet.HEADERS,
                List.of(
                        row("R20260915-ABCD1234", "롯데택배", "123456789012"),
                        row("R20260915-EFGH5678", "", "987654321098")),
                null);

        List<OrderTrackingSheet.Row> rows = OrderTrackingSheet.parse(file);

        assertThat(rows).hasSize(2);
        assertThat(rows.get(0).rowNo()).isEqualTo(2);
        assertThat(rows.get(0).orderNo()).isEqualTo("R20260915-ABCD1234");
        assertThat(rows.get(0).carrier()).isEqualTo("롯데택배");
        assertThat(rows.get(0).trackingNo()).isEqualTo("123456789012");
        // 택배사를 비워 보내면 기본 택배사를 쓴다 (판단은 OrderService 가 한다)
        assertThat(rows.get(1).carrier()).isEmpty();
        assertThat(rows.get(1).trackingNo()).isEqualTo("987654321098");
    }

    @Test
    @DisplayName("엑셀이 저장한 공유 문자열 파일도 읽는다")
    void readsExcelSavedSheet() {
        byte[] file = excelStyleSheet();

        List<OrderTrackingSheet.Row> rows = OrderTrackingSheet.parse(file);

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).orderNo()).isEqualTo("R20260915-ABCD1234");
        assertThat(rows.get(0).carrier()).isEqualTo("CJ대한통운");
        // 숫자 칸으로 저장된 송장번호도 그대로 읽힌다
        assertThat(rows.get(0).trackingNo()).isEqualTo("601234567890");
    }

    @Test
    @DisplayName("칸 이름이 달라도 찾는다 — 운송장번호, 주문 번호, 배송사")
    void findsColumnsByOtherNames() {
        byte[] file = SimpleXlsxWriter.write("송장",
                List.of("연번", "주문 번호", "받는분", "배송사", "운송장번호"),
                List.of(List.of("1", "R20260915-ABCD1234", "김받는", "한진택배", "555000111222")),
                null);

        List<OrderTrackingSheet.Row> rows = OrderTrackingSheet.parse(file);

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).orderNo()).isEqualTo("R20260915-ABCD1234");
        assertThat(rows.get(0).carrier()).isEqualTo("한진택배");
        assertThat(rows.get(0).trackingNo()).isEqualTo("555000111222");
    }

    @Test
    @DisplayName("머리글 위에 제목 줄이 있어도 찾는다")
    void findsHeaderBelowTitleRows() {
        byte[] file = SimpleXlsxWriter.write("송장",
                List.of("와이에스컴퍼니 출고 내역"),
                List.of(
                        List.of("2026-09-18"),
                        List.of(),
                        List.of("주문번호", "송장번호"),
                        List.of("R20260915-ABCD1234", "123456789012")),
                null);

        List<OrderTrackingSheet.Row> rows = OrderTrackingSheet.parse(file);

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).rowNo()).isEqualTo(5);
        assertThat(rows.get(0).orderNo()).isEqualTo("R20260915-ABCD1234");
    }

    @Test
    @DisplayName("송장 칸이 없으면 무엇이 잘못됐는지 알려준다")
    void refusesSheetWithoutTrackingColumn() {
        byte[] file = SimpleXlsxWriter.write("주문",
                List.of("주문번호", "받는 분"),
                List.of(List.of("R20260915-ABCD1234", "김받는")),
                null);

        assertThatThrownBy(() -> OrderTrackingSheet.parse(file))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("송장번호");
    }

    @Test
    @DisplayName("엑셀이 아닌 파일은 읽지 않는다")
    void refusesNonExcel() {
        assertThatThrownBy(() -> OrderTrackingSheet.parse("그냥 글자".getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /** 출고 엑셀 한 줄 — 맨 뒤 두 칸에 송장을 채운 모양. */
    private static List<Object> row(String orderNo, String carrier, String trackingNo) {
        List<Object> cells = new java.util.ArrayList<>();
        cells.add(orderNo);
        while (cells.size() < OrderShippingSheet.HEADERS.size() - 2) {
            cells.add("");
        }
        cells.add(carrier);
        cells.add(trackingNo);
        return cells;
    }

    /**
     * 엑셀이 저장한 형태의 파일을 손으로 만든다.
     * 글자는 sharedStrings 에 모아 t="s" 로 가리키고, 송장번호는 숫자 칸으로 넣는다.
     */
    private static byte[] excelStyleSheet() {
        String shared = """
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <sst xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" count="6" uniqueCount="6">
                <si><t>주문번호</t></si><si><t>택배사</t></si><si><t>송장번호</t></si>
                <si><t>R20260915-ABCD1234</t></si><si><t>CJ대한통운</t></si><si><t>여분</t></si>
                </sst>
                """;
        String sheet = """
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><sheetData>
                <row r="1"><c r="A1" t="s"><v>0</v></c><c r="B1" t="s"><v>1</v></c><c r="C1" t="s"><v>2</v></c></row>
                <row r="2"><c r="A2" t="s"><v>3</v></c><c r="B2" t="s"><v>4</v></c><c r="C2"><v>601234567890</v></c></row>
                </sheetData></worksheet>
                """;
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(buf, StandardCharsets.UTF_8)) {
            zip.putNextEntry(new ZipEntry("xl/sharedStrings.xml"));
            zip.write(shared.getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
            zip.putNextEntry(new ZipEntry("xl/worksheets/sheet1.xml"));
            zip.write(sheet.getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        return buf.toByteArray();
    }
}
