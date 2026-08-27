package com.rizenfood.api.common;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.safety.Safelist;
import org.springframework.stereotype.Component;

/**
 * 에디터 입력을 저장 전에 걸러낸다.
 *
 * ★ 허용 목록 방식이다. "위험한 것을 지우는" 방식은 반드시 뚫린다.
 *   공격 문법은 계속 새로 나오고, 차단 목록은 항상 그보다 늦다.
 *   그래서 반대로 간다 — 명시적으로 허락한 태그와 속성만 남기고 나머지는 전부 버린다.
 *
 * 저장형 XSS 는 한 번 들어가면 그 페이지를 보는 모든 사람에게 실행된다.
 * 상품 설명·공지 본문이 그 경로다 (기획서 §10).
 *
 * 살균은 반드시 저장 시점에 한다. 출력 시에만 하면 어딘가에서 빠뜨린 출력 경로가
 * 생기는 순간 뚫리고, DB 에는 이미 오염된 값이 쌓인다.
 */
@Component
public class HtmlSanitizer {

    private final Safelist safelist;

    public HtmlSanitizer() {
        this.safelist = Safelist.relaxed()
                // 이미지 정렬·크기 지정에 쓰는 최소한의 속성
                .addAttributes("img", "src", "alt", "width", "height")
                .addAttributes("a", "href", "title", "target", "rel")
                .addAttributes("table", "summary")
                .addAttributes("td", "colspan", "rowspan")
                .addAttributes("th", "colspan", "rowspan", "scope")

                // 이미지·링크는 우리 CDN 과 http(s) 만 허용한다.
                // 이게 없으면 javascript: 나 data: 스킴으로 코드를 넣을 수 있다.
                .addProtocols("img", "src", "http", "https")
                .addProtocols("a", "href", "http", "https", "mailto")

                // 인라인 스타일은 통째로 막는다.
                // style 안에서도 코드 실행이 가능하고, 브랜드 디자인이 깨진다.
                .removeAttributes(":all", "style", "class", "id")
                .removeTags("script", "style", "iframe", "object", "embed", "form",
                            "input", "button", "link", "meta", "base");
    }

    /**
     * @param dirty 에디터가 보낸 HTML
     * @return 허용된 것만 남은 HTML. 입력이 null 이면 null.
     */
    public String clean(String dirty) {
        if (dirty == null) {
            return null;
        }
        if (dirty.isBlank()) {
            return "";
        }

        // baseUri 를 비워 상대 경로가 엉뚱한 곳으로 해석되지 않게 한다.
        String cleaned = Jsoup.clean(dirty, "", safelist,
                new Document.OutputSettings().prettyPrint(false));

        // 외부로 나가는 링크에는 rel 을 붙여 원본 탭 탈취를 막는다.
        return cleaned.replace("<a href=\"http", "<a rel=\"noopener noreferrer\" href=\"http");
    }
}
