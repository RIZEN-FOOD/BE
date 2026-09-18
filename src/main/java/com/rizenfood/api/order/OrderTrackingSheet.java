package com.rizenfood.api.order;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.rizenfood.api.common.SimpleXlsxReader;

/**
 * 출고 대행사(3PL)가 송장을 채워 돌려준 엑셀을 읽는다. {@link OrderShippingSheet} 의 짝이다.
 *
 * 대행사마다 양식이 조금씩 다르므로 <b>칸 순서를 믿지 않고 머리글 이름으로 찾는다.</b>
 *   주문번호 — "주문번호", "주문 번호", "order no" 처럼 적혀 있어도 찾는다
 *   송장번호 — "송장번호", "운송장번호", "운송장" 등
 *   택배사   — 없으면 관리자 설정의 기본 택배사를 쓴다
 *
 * 머리글이 첫 줄에 없을 수도 있다(대행사가 위에 제목 줄을 넣는 경우). 그래서 앞쪽 몇 줄을
 * 훑어 주문번호·송장번호가 같이 있는 줄을 머리글로 본다.
 */
final class OrderTrackingSheet {

    /** 한 번에 처리할 최대 줄 수. 하루 주문이 이보다 많아지면 올린다. */
    static final int MAX_ROWS = 5000;

    /** 머리글을 찾아볼 앞쪽 줄 수. */
    private static final int HEADER_SCAN_ROWS = 10;

    private static final List<String> ORDER_NO_NAMES = List.of("주문번호", "주문no", "orderno", "ordernumber");
    private static final List<String> TRACKING_NAMES = List.of("송장번호", "운송장번호", "운송장", "송장", "invoiceno", "trackingno");
    private static final List<String> CARRIER_NAMES = List.of("택배사", "배송사", "운송사", "carrier");

    /**
     * 엑셀 한 줄에서 읽어낸 송장.
     *
     * @param rowNo 사람이 보는 줄 번호(1부터). 실패를 알려줄 때 쓴다.
     */
    record Row(int rowNo, String orderNo, String carrier, String trackingNo) {
    }

    private OrderTrackingSheet() {
    }

    /**
     * @throws IllegalArgumentException 머리글을 찾지 못했을 때
     * @return 주문번호와 송장번호가 모두 있는 줄만. 빈 줄은 조용히 버린다.
     */
    static List<Row> parse(byte[] file) {
        List<List<String>> rows = SimpleXlsxReader.read(file, MAX_ROWS);

        int headerAt = -1;
        int orderCol = -1;
        int trackingCol = -1;
        int carrierCol = -1;
        for (int i = 0; i < Math.min(HEADER_SCAN_ROWS, rows.size()); i++) {
            int order = find(rows.get(i), ORDER_NO_NAMES);
            int tracking = find(rows.get(i), TRACKING_NAMES);
            if (order >= 0 && tracking >= 0) {
                headerAt = i;
                orderCol = order;
                trackingCol = tracking;
                carrierCol = find(rows.get(i), CARRIER_NAMES);
                break;
            }
        }
        if (headerAt < 0) {
            throw new IllegalArgumentException(
                    "엑셀에서 '주문번호'와 '송장번호' 칸을 찾지 못했습니다. 내려받은 출고 엑셀에 송장을 채워 올려 주세요.");
        }

        List<Row> out = new ArrayList<>();
        for (int i = headerAt + 1; i < rows.size(); i++) {
            List<String> row = rows.get(i);
            String orderNo = cell(row, orderCol);
            String trackingNo = cell(row, trackingCol);
            if (orderNo.isEmpty() && trackingNo.isEmpty()) {
                continue; // 빈 줄
            }
            out.add(new Row(i + 1, orderNo, cell(row, carrierCol), trackingNo));
        }
        return out;
    }

    /** 머리글 줄에서 이름이 맞는 칸 번호. 없으면 -1. */
    private static int find(List<String> header, List<String> names) {
        for (int c = 0; c < header.size(); c++) {
            String text = normalize(header.get(c));
            if (text.isEmpty()) {
                continue;
            }
            for (String name : names) {
                if (text.equals(name) || text.contains(name)) {
                    return c;
                }
            }
        }
        return -1;
    }

    /** 띄어쓰기·괄호·대소문자 차이를 없앤다. "주문 번호(필수)" → "주문번호필수" */
    private static String normalize(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.toLowerCase(Locale.ROOT).replaceAll("[\\s()\\[\\]_.-]", "");
    }

    private static String cell(List<String> row, int col) {
        if (col < 0 || col >= row.size() || row.get(col) == null) {
            return "";
        }
        return row.get(col).trim();
    }
}
