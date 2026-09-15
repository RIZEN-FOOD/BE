package com.rizenfood.api.common;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * 시트 하나짜리 엑셀(.xlsx) 파일을 만든다. 라이브러리 없이 표준 형식(OOXML)을 직접 쓴다.
 *
 * CSV 대신 xlsx 인 이유:
 *   - CSV 를 엑셀로 열면 우편번호 06234 → 6234 처럼 앞자리 0 이 사라진다 (서울 주소 오배송).
 *   - 글자는 전부 "문자열 칸"으로 넣어, '=' 로 시작하는 값도 수식으로 실행되지 않는다 (수식 주입 방지).
 *
 * 칸 값: String → 문자열(텍스트 서식), Number → 숫자, null → 빈 칸.
 */
public final class SimpleXlsxWriter {

    private SimpleXlsxWriter() {
    }

    public static byte[] write(String sheetName, List<String> headers, List<? extends List<?>> rows,
                               int[] columnWidths) {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(buf, StandardCharsets.UTF_8)) {
            put(zip, "[Content_Types].xml", CONTENT_TYPES);
            put(zip, "_rels/.rels", ROOT_RELS);
            put(zip, "xl/workbook.xml", workbook(sheetName));
            put(zip, "xl/_rels/workbook.xml.rels", WORKBOOK_RELS);
            put(zip, "xl/styles.xml", STYLES);
            put(zip, "xl/worksheets/sheet1.xml", sheet(headers, rows, columnWidths));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return buf.toByteArray();
    }

    private static void put(ZipOutputStream zip, String name, String xml) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(xml.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }

    private static String sheet(List<String> headers, List<? extends List<?>> rows, int[] widths) {
        StringBuilder sb = new StringBuilder(4096);
        sb.append(XML_DECL)
          .append("<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">")
          // 머리글 한 줄 고정
          .append("<sheetViews><sheetView workbookViewId=\"0\">")
          .append("<pane ySplit=\"1\" topLeftCell=\"A2\" activePane=\"bottomLeft\" state=\"frozen\"/>")
          .append("</sheetView></sheetViews>");
        if (widths != null && widths.length > 0) {
            sb.append("<cols>");
            for (int i = 0; i < widths.length; i++) {
                sb.append("<col min=\"").append(i + 1).append("\" max=\"").append(i + 1)
                  .append("\" width=\"").append(widths[i]).append("\" customWidth=\"1\"/>");
            }
            sb.append("</cols>");
        }
        sb.append("<sheetData>");
        appendRow(sb, 1, headers, STYLE_HEADER);
        int r = 2;
        for (List<?> row : rows) {
            appendRow(sb, r++, row, STYLE_TEXT);
        }
        sb.append("</sheetData></worksheet>");
        return sb.toString();
    }

    private static void appendRow(StringBuilder sb, int rowNo, List<?> cells, int textStyle) {
        sb.append("<row r=\"").append(rowNo).append("\">");
        for (int c = 0; c < cells.size(); c++) {
            Object v = cells.get(c);
            if (v == null) {
                continue;
            }
            String ref = columnName(c) + rowNo;
            if (v instanceof Number n) {
                sb.append("<c r=\"").append(ref).append("\"><v>").append(n).append("</v></c>");
            } else {
                sb.append("<c r=\"").append(ref).append("\" t=\"inlineStr\" s=\"").append(textStyle)
                  .append("\"><is><t xml:space=\"preserve\">").append(escape(v.toString()))
                  .append("</t></is></c>");
            }
        }
        sb.append("</row>");
    }

    /** 0 → A, 25 → Z, 26 → AA. */
    static String columnName(int index) {
        StringBuilder sb = new StringBuilder();
        int n = index + 1;
        while (n > 0) {
            int rem = (n - 1) % 26;
            sb.insert(0, (char) ('A' + rem));
            n = (n - 1) / 26;
        }
        return sb.toString();
    }

    /** XML 특수문자를 바꾸고, XML 에 넣을 수 없는 제어문자는 뺀다. */
    static String escape(String s) {
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            switch (ch) {
                case '&' -> sb.append("&amp;");
                case '<' -> sb.append("&lt;");
                case '>' -> sb.append("&gt;");
                case '"' -> sb.append("&quot;");
                default -> {
                    boolean control = ch < 0x20 && ch != '\t' && ch != '\n' && ch != '\r';
                    if (!control && ch != 0xFFFE && ch != 0xFFFF) {
                        sb.append(ch);
                    }
                }
            }
        }
        return sb.toString();
    }

    private static String workbook(String sheetName) {
        return XML_DECL
                + "<workbook xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\""
                + " xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\">"
                + "<sheets><sheet name=\"" + escape(sheetName) + "\" sheetId=\"1\" r:id=\"rId1\"/></sheets>"
                + "</workbook>";
    }

    private static final int STYLE_HEADER = 1;
    private static final int STYLE_TEXT = 2;

    private static final String XML_DECL = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>";

    private static final String CONTENT_TYPES = XML_DECL
            + "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">"
            + "<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>"
            + "<Default Extension=\"xml\" ContentType=\"application/xml\"/>"
            + "<Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/>"
            + "<Override PartName=\"/xl/worksheets/sheet1.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>"
            + "<Override PartName=\"/xl/styles.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml\"/>"
            + "</Types>";

    private static final String ROOT_RELS = XML_DECL
            + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"
            + "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"xl/workbook.xml\"/>"
            + "</Relationships>";

    private static final String WORKBOOK_RELS = XML_DECL
            + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"
            + "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet1.xml\"/>"
            + "<Relationship Id=\"rId2\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles\" Target=\"styles.xml\"/>"
            + "</Relationships>";

    // 0: 기본, 1: 머리글(굵게), 2: 텍스트 서식(@) — 셀을 고쳐도 앞자리 0 이 유지된다.
    private static final String STYLES = XML_DECL
            + "<styleSheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">"
            + "<fonts count=\"2\">"
            + "<font><sz val=\"11\"/><name val=\"맑은 고딕\"/></font>"
            + "<font><b/><sz val=\"11\"/><name val=\"맑은 고딕\"/></font>"
            + "</fonts>"
            + "<fills count=\"2\"><fill><patternFill patternType=\"none\"/></fill>"
            + "<fill><patternFill patternType=\"gray125\"/></fill></fills>"
            + "<borders count=\"1\"><border><left/><right/><top/><bottom/><diagonal/></border></borders>"
            + "<cellStyleXfs count=\"1\"><xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\"/></cellStyleXfs>"
            + "<cellXfs count=\"3\">"
            + "<xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\" xfId=\"0\"/>"
            + "<xf numFmtId=\"0\" fontId=\"1\" fillId=\"0\" borderId=\"0\" xfId=\"0\" applyFont=\"1\"/>"
            + "<xf numFmtId=\"49\" fontId=\"0\" fillId=\"0\" borderId=\"0\" xfId=\"0\" applyNumberFormat=\"1\"/>"
            + "</cellXfs>"
            + "<cellStyles count=\"1\"><cellStyle name=\"Normal\" xfId=\"0\" builtinId=\"0\"/></cellStyles>"
            + "</styleSheet>";
}
