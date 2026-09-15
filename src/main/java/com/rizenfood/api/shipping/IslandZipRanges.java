package com.rizenfood.api.shipping;

import java.util.ArrayList;
import java.util.List;

/**
 * 도서산간 우편번호 범위.
 *
 * 형식: "63000-63644, 40200-40240, 54000" — 쉼표·줄바꿈·공백으로 나누고, 범위는 '-' 또는 '~'.
 * 5자리가 아닌 조각은 조용히 버린다 (관리자가 잘못 넣어도 주문이 막히지 않게).
 *
 * 기본 목록은 쇼핑몰에서 널리 쓰는 도서산간 목록(제주·인천 옹진·울릉·전남 신안·완도 등)이다.
 * 택배사·출고 대행사 목록이 다르면 사이트 설정 shipping.island_zip_ranges 에 넣어 덮어쓴다.
 */
public final class IslandZipRanges {

    public static final String DEFAULT_SPEC =
            "63000-63644, "                                    // 제주
            + "22386-22388, 23004-23010, 23100-23116, 23124-23136, " // 인천 중구·강화·옹진 섬
            + "31708, 32133, "                                 // 충남 당진·태안 섬
            + "40200-40240, "                                  // 경북 울릉
            + "46768-46771, "                                  // 부산 강서 섬
            + "52570-52571, 53031-53033, 53089-53104, "        // 경남 사천·통영 섬
            + "54000, 56347-56349, "                           // 전북 군산·부안 섬
            + "57068-57069, 58760-58762, "                     // 전남 영광·목포 섬
            + "58800-58810, 58816-58818, 58826, 58828-58866, " // 전남 신안
            + "58953-58958, "                                  // 전남 진도 섬
            + "59102-59103, 59106, 59127, 59129, 59137-59166, " // 전남 완도
            + "59650, 59766, 59781-59790";                     // 전남 여수 섬

    public static final IslandZipRanges DEFAULT = parse(DEFAULT_SPEC);

    private final List<int[]> ranges;

    private IslandZipRanges(List<int[]> ranges) {
        this.ranges = ranges;
    }

    public static IslandZipRanges parse(String spec) {
        List<int[]> out = new ArrayList<>();
        if (spec != null) {
            for (String token : spec.split("[,\\s]+")) {
                String[] ends = token.split("[-~]", -1);
                if (ends.length == 1 && isZip(ends[0])) {
                    int z = Integer.parseInt(ends[0]);
                    out.add(new int[] {z, z});
                } else if (ends.length == 2 && isZip(ends[0]) && isZip(ends[1])) {
                    int a = Integer.parseInt(ends[0]);
                    int b = Integer.parseInt(ends[1]);
                    out.add(new int[] {Math.min(a, b), Math.max(a, b)});
                }
            }
        }
        return new IslandZipRanges(List.copyOf(out));
    }

    public boolean isEmpty() {
        return ranges.isEmpty();
    }

    /** 5자리 우편번호가 목록 안에 드는지. 형식이 틀리면 false. */
    public boolean contains(String zipcode) {
        if (zipcode == null) {
            return false;
        }
        String z = zipcode.trim();
        if (!isZip(z)) {
            return false;
        }
        int n = Integer.parseInt(z);
        for (int[] r : ranges) {
            if (n >= r[0] && n <= r[1]) {
                return true;
            }
        }
        return false;
    }

    private static boolean isZip(String s) {
        return s.length() == 5 && s.chars().allMatch(c -> c >= '0' && c <= '9');
    }
}
