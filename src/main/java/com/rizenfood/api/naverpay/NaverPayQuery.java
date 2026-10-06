package com.rizenfood.api.naverpay;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 네이버페이가 보내는 조회 주소의 파라미터를 읽는다.
 *
 * 네이버는 PHP 식 배열 이름을 쓴다 — product[0][id]=1&product[1][id]=2 (상품정보, 표 3-7),
 * productId[0]=1&productId[1]=2&zipcode=13591 (도서산간비, 표 3-13).
 * 스프링의 기본 바인딩은 이 이름을 배열로 모으지 못해 원문 쿼리를 직접 읽는다.
 *
 * ★ 대괄호가 인코딩 없이 오면 톰캣이 400 으로 거절한다. application.yml 의
 *   server.tomcat.relaxed-query-chars 로 풀어 두었다.
 */
final class NaverPayQuery {

    /** product[0][id], product[0][ecmallproductId] (대소문자 무시) */
    private static final Pattern PRODUCT_ID = Pattern.compile("^product\\[(\\d{1,3})]\\[(id|ecmallproductid)]$",
            Pattern.CASE_INSENSITIVE);

    /** productId[0] — 가이드 예시에 product[0] 표기도 섞여 있어 둘 다 받는다 */
    private static final Pattern FEE_PRODUCT_ID = Pattern.compile("^product(?:id)?\\[(\\d{1,3})]$",
            Pattern.CASE_INSENSITIVE);

    /** 한 번에 조회하는 상품 수 상한. 터무니없이 긴 요청으로 DB 를 훑지 못하게. */
    static final int MAX_IDS = 50;

    private NaverPayQuery() {
    }

    /** 상품정보 요청에서 상품 번호들을 순번대로. id 가 없으면 ecmallproductId 를 쓴다. */
    static List<String> productInfoIds(String rawQuery) {
        Map<Integer, String> ids = new TreeMap<>();
        Map<Integer, String> fallback = new TreeMap<>();
        for (String[] kv : pairs(rawQuery)) {
            Matcher m = PRODUCT_ID.matcher(kv[0]);
            if (m.matches()) {
                int index = Integer.parseInt(m.group(1));
                if ("id".equalsIgnoreCase(m.group(2))) {
                    ids.put(index, kv[1]);
                } else {
                    fallback.put(index, kv[1]);
                }
            }
        }
        fallback.forEach(ids::putIfAbsent);
        return limit(new ArrayList<>(ids.values()));
    }

    /** 도서산간비 요청에서 상품 번호들을 순번대로. */
    static List<String> additionalFeeIds(String rawQuery) {
        Map<Integer, String> ids = new TreeMap<>();
        for (String[] kv : pairs(rawQuery)) {
            Matcher m = FEE_PRODUCT_ID.matcher(kv[0]);
            if (m.matches()) {
                ids.put(Integer.parseInt(m.group(1)), kv[1]);
            }
        }
        return limit(new ArrayList<>(ids.values()));
    }

    /** 파라미터 하나의 값. 없으면 null. */
    static String param(String rawQuery, String name) {
        for (String[] kv : pairs(rawQuery)) {
            if (kv[0].equalsIgnoreCase(name)) {
                return kv[1];
            }
        }
        return null;
    }

    private static List<String> limit(List<String> ids) {
        List<String> cleaned = ids.stream().map(String::trim).filter(s -> !s.isEmpty()).toList();
        return cleaned.size() > MAX_IDS ? cleaned.subList(0, MAX_IDS) : cleaned;
    }

    private static List<String[]> pairs(String rawQuery) {
        List<String[]> out = new ArrayList<>();
        if (rawQuery == null || rawQuery.isBlank()) {
            return out;
        }
        for (String part : rawQuery.split("&")) {
            if (part.isEmpty()) {
                continue;
            }
            int eq = part.indexOf('=');
            String key = decode(eq < 0 ? part : part.substring(0, eq));
            String value = eq < 0 ? "" : decode(part.substring(eq + 1));
            out.add(new String[] {key, value});
        }
        return out;
    }

    private static String decode(String s) {
        try {
            return URLDecoder.decode(s, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            return s; // 잘못된 % 인코딩은 원문 그대로 — 어차피 패턴에 맞지 않아 버려진다
        }
    }
}
