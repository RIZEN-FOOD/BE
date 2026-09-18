package com.rizenfood.api.common;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

/**
 * 시트 하나짜리 엑셀(.xlsx)을 읽어 문자열 표로 돌려준다. {@link SimpleXlsxWriter} 의 짝이다.
 *
 * 라이브러리를 더하지 않은 이유는 쓰기 쪽과 같다 — 우리가 필요한 건 "칸에 적힌 글자"뿐이고
 * 서식·수식·차트는 볼 일이 없다.
 *
 * 읽을 수 있는 칸
 *   - 우리가 만든 파일의 inlineStr
 *   - 엑셀이 저장한 공유 문자열(t="s") 과 일반 문자열(t="str")
 *   - 숫자 (송장번호를 숫자로 저장해 버리는 엑셀 때문에 필요하다)
 *
 * ★ 남이 보낸 zip 을 푸는 일이므로 압축 해제 크기와 칸 수에 상한을 둔다(zip bomb 방지).
 * ★ 외부 엔티티를 따라가지 않는다(XXE 방지).
 * ★ 값은 전부 글자로만 다룬다. 수식은 계산하지 않는다.
 */
public final class SimpleXlsxReader {

    /** 압축을 푼 뒤 총 바이트 상한. 주문 수천 건짜리 시트도 이 안에 들어온다. */
    private static final long MAX_UNCOMPRESSED_BYTES = 40L * 1024 * 1024;
    private static final int MAX_ENTRIES = 200;
    private static final int MAX_COLUMNS = 200;

    private SimpleXlsxReader() {
    }

    /**
     * @param maxRows 읽을 최대 줄 수(머리글 포함). 넘으면 거기서 자른다.
     * @return 줄 목록. 빈 칸은 빈 글자, 뒤쪽이 빈 줄은 그대로 짧게 온다.
     */
    public static List<List<String>> read(InputStream in, int maxRows) {
        Map<String, byte[]> files = unzip(in);
        byte[] sheet = firstSheet(files);
        if (sheet == null) {
            throw new IllegalArgumentException("엑셀 파일이 아니거나 시트를 찾을 수 없습니다.");
        }
        List<String> shared = sharedStrings(files.get("xl/sharedStrings.xml"));
        return parseSheet(sheet, shared, maxRows);
    }

    /** 바이트 배열로 받는 편의 메서드. */
    public static List<List<String>> read(byte[] file, int maxRows) {
        try (InputStream in = new ByteArrayInputStream(file)) {
            return read(in, maxRows);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    // ── zip ──────────────────────────────────────────────────

    private static Map<String, byte[]> unzip(InputStream in) {
        Map<String, byte[]> files = new HashMap<>();
        long total = 0;
        int count = 0;
        try (ZipInputStream zip = new ZipInputStream(in, StandardCharsets.UTF_8)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    continue;
                }
                if (++count > MAX_ENTRIES) {
                    throw new IllegalArgumentException("엑셀 파일의 구성이 올바르지 않습니다.");
                }
                String name = entry.getName();
                // 우리가 볼 파일만 읽는다. 그림 같은 건 통째로 건너뛴다.
                if (!name.equals("xl/sharedStrings.xml") && !name.startsWith("xl/worksheets/")) {
                    continue;
                }
                byte[] data = zip.readAllBytes();
                total += data.length;
                if (total > MAX_UNCOMPRESSED_BYTES) {
                    throw new IllegalArgumentException("엑셀 파일이 너무 큽니다.");
                }
                files.put(name, data);
            }
        } catch (IOException e) {
            throw new IllegalArgumentException("엑셀 파일을 열 수 없습니다.");
        }
        return files;
    }

    /** 첫 번째 시트. sheet1.xml 이 있으면 그것, 없으면 이름순으로 가장 앞. */
    private static byte[] firstSheet(Map<String, byte[]> files) {
        byte[] first = files.get("xl/worksheets/sheet1.xml");
        if (first != null) {
            return first;
        }
        TreeMap<String, byte[]> sorted = new TreeMap<>();
        files.forEach((name, data) -> {
            if (name.startsWith("xl/worksheets/") && name.endsWith(".xml")) {
                sorted.put(name, data);
            }
        });
        return sorted.isEmpty() ? null : sorted.firstEntry().getValue();
    }

    // ── xml ──────────────────────────────────────────────────

    private static XMLStreamReader reader(byte[] xml) throws XMLStreamException {
        XMLInputFactory factory = XMLInputFactory.newInstance();
        factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
        factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
        return factory.createXMLStreamReader(new ByteArrayInputStream(xml));
    }

    /** 공유 문자열 표. 엑셀이 저장한 파일은 글자를 전부 여기에 모아 둔다. */
    private static List<String> sharedStrings(byte[] xml) {
        List<String> out = new ArrayList<>();
        if (xml == null) {
            return out;
        }
        try {
            XMLStreamReader r = reader(xml);
            StringBuilder item = null;
            while (r.hasNext()) {
                int event = r.next();
                if (event == XMLStreamConstants.START_ELEMENT) {
                    if ("si".equals(r.getLocalName())) {
                        item = new StringBuilder();
                    } else if ("t".equals(r.getLocalName()) && item != null) {
                        item.append(r.getElementText());
                    }
                } else if (event == XMLStreamConstants.END_ELEMENT
                        && "si".equals(r.getLocalName()) && item != null) {
                    out.add(item.toString());
                    item = null;
                }
            }
            r.close();
        } catch (XMLStreamException e) {
            throw new IllegalArgumentException("엑셀 파일을 읽을 수 없습니다.");
        }
        return out;
    }

    private static List<List<String>> parseSheet(byte[] xml, List<String> shared, int maxRows) {
        List<List<String>> rows = new ArrayList<>();
        try {
            XMLStreamReader r = reader(xml);
            List<String> row = null;
            String cellType = null;
            int cellIndex = -1;
            String value = null;
            boolean inlineText = false;

            while (r.hasNext() && rows.size() < maxRows) {
                int event = r.next();
                if (event == XMLStreamConstants.START_ELEMENT) {
                    switch (r.getLocalName()) {
                        case "row" -> row = new ArrayList<>();
                        case "c" -> {
                            cellType = r.getAttributeValue(null, "t");
                            cellIndex = columnIndex(r.getAttributeValue(null, "r"));
                            value = null;
                            inlineText = false;
                        }
                        case "is" -> inlineText = true;
                        case "t" -> {
                            if (inlineText) {
                                value = r.getElementText();
                            }
                        }
                        case "v" -> value = r.getElementText();
                        default -> { }
                    }
                } else if (event == XMLStreamConstants.END_ELEMENT) {
                    switch (r.getLocalName()) {
                        case "c" -> {
                            if (row != null) {
                                put(row, cellIndex, resolve(cellType, value, shared));
                            }
                        }
                        case "is" -> inlineText = false;
                        case "row" -> {
                            if (row != null) {
                                rows.add(row);
                                row = null;
                            }
                        }
                        default -> { }
                    }
                }
            }
            r.close();
        } catch (XMLStreamException e) {
            throw new IllegalArgumentException("엑셀 파일을 읽을 수 없습니다.");
        }
        return rows;
    }

    /** 칸 하나를 제자리에 넣는다. 빈 칸은 아예 빠져서 오므로 앞을 빈 글자로 채운다. */
    private static void put(List<String> row, int index, String text) {
        int at = index < 0 ? row.size() : index;
        if (at >= MAX_COLUMNS) {
            return;
        }
        while (row.size() <= at) {
            row.add("");
        }
        row.set(at, text);
    }

    private static String resolve(String type, String value, List<String> shared) {
        if (value == null) {
            return "";
        }
        if ("s".equals(type)) {
            try {
                int i = Integer.parseInt(value.trim());
                return i >= 0 && i < shared.size() ? shared.get(i) : "";
            } catch (NumberFormatException e) {
                return "";
            }
        }
        if ("e".equals(type)) {
            return ""; // 오류 칸(#N/A 등)
        }
        return value;
    }

    /** "C12" → 2 (0부터). 주소가 없으면 -1. */
    private static int columnIndex(String ref) {
        if (ref == null || ref.isEmpty()) {
            return -1;
        }
        int col = 0;
        for (int i = 0; i < ref.length(); i++) {
            char c = ref.charAt(i);
            if (c >= 'A' && c <= 'Z') {
                col = col * 26 + (c - 'A' + 1);
            } else if (c >= 'a' && c <= 'z') {
                col = col * 26 + (c - 'a' + 1);
            } else {
                break;
            }
        }
        return col - 1;
    }
}
