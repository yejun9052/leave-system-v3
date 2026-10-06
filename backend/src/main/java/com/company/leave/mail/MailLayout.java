package com.company.leave.mail;

import java.util.ArrayList;
import java.util.List;

/**
 * 안내 메일 공통 모양: 제목 → 안내 문장 → 표(수신자·처리자 | 내용) → 바로가기 링크.
 * 같은 내용을 HTML(메일 앱 표시용)과 일반 텍스트(HTML 을 못 여는 메일 앱용)로 함께 만든다.
 * HTML 은 메일 앱이 &lt;style&gt; 을 지우는 경우가 많아 모든 스타일을 태그에 직접 넣고, 값은 모두 이스케이프한다.
 */
public final class MailLayout {

    private static final String FONT = "-apple-system,BlinkMacSystemFont,'Apple SD Gothic Neo','Malgun Gothic',"
            + "'맑은 고딕',sans-serif";
    private static final String BORDER = "#d1d5db";

    private MailLayout() {
    }

    /** 바로가기 링크 하나. */
    public record Link(String label, String url) {
    }

    /** 표 한 줄. strong 이면 값을 굵게(처리 상태 등). */
    public record Row(String label, String value, boolean strong) {

        public static Row of(String label, String value) {
            return new Row(label, value, false);
        }

        public static Row strong(String label, String value) {
            return new Row(label, value, true);
        }
    }

    /**
     * 메일 내용.
     *
     * @param title     맨 위 큰 제목(예: "휴가 등록 안내")
     * @param intro     안내 문장(줄마다 한 줄)
     * @param head      표 윗부분(수신자·처리자). 아래 내용과 굵은 선으로 나뉜다
     * @param rows      표 내용
     * @param linkLabel 바로가기 문구(예: "내 휴가 확인하기")
     * @param linkUrl   바로가기 주소
     * @param moreLinks 바로가기 아래에 덧붙이는 링크(한 통을 여럿이 받을 때 각자의 화면). 보통 비어 있다
     */
    public record Content(String title, List<String> intro, List<Row> head, List<Row> rows,
                          String linkLabel, String linkUrl, List<Link> moreLinks) {

        public Content(String title, List<String> intro, List<Row> head, List<Row> rows,
                       String linkLabel, String linkUrl) {
            this(title, intro, head, rows, linkLabel, linkUrl, List.of());
        }

        /** 표 맨 위에 수신자 줄을 넣은 사본. */
        public Content withRecipient(String recipient) {
            return withTop(List.of(Row.of("수신자", recipient)));
        }

        /** 표 맨 위에 수신자·참조 줄을 넣은 사본(한 통을 함께 받을 때). */
        public Content withRecipient(String recipient, String cc) {
            return withTop(List.of(Row.of("수신자", recipient), Row.of("참조", cc)));
        }

        /** 바로가기 링크를 하나 더 붙인 사본. */
        public Content withLink(String label, String url) {
            List<Link> links = new ArrayList<>(moreLinks);
            links.add(new Link(label, url));
            return new Content(title, intro, head, rows, linkLabel, linkUrl, List.copyOf(links));
        }

        private Content withTop(List<Row> top) {
            List<Row> merged = new ArrayList<>(top);
            merged.addAll(head);
            return new Content(title, intro, merged, rows, linkLabel, linkUrl, moreLinks);
        }

        public String text() {
            StringBuilder sb = new StringBuilder(title).append("\n\n");
            intro.forEach(line -> sb.append(line).append('\n'));
            sb.append('\n');
            if (!head.isEmpty()) {
                head.forEach(r -> sb.append(r.label()).append(": ").append(r.value()).append('\n'));
                sb.append('\n');
            }
            rows.forEach(r -> sb.append(r.label()).append(": ").append(r.value()).append('\n'));
            sb.append('\n').append(linkLabel).append(": ").append(linkUrl).append('\n');
            moreLinks.forEach(l -> sb.append(l.label()).append(": ").append(l.url()).append('\n'));
            return sb.toString();
        }

        public String html() {
            StringBuilder sb = new StringBuilder()
                    .append("<!doctype html><html lang=\"ko\"><head><meta charset=\"UTF-8\">")
                    .append("<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\"></head>")
                    .append("<body style=\"margin:0;padding:0;background:#ffffff;\">")
                    .append("<div style=\"max-width:600px;margin:0 auto;padding:32px 20px;font-family:").append(FONT)
                    .append(";color:#1f2937;\">")
                    .append("<h1 style=\"margin:0 0 20px;font-size:22px;font-weight:700;color:#111827;\">")
                    .append(escape(title)).append("</h1>")
                    .append("<p style=\"margin:0 0 24px;font-size:15px;line-height:1.7;\">")
                    .append(String.join("<br>", intro.stream().map(MailLayout::escape).toList()))
                    .append("</p>")
                    .append("<table role=\"presentation\" cellpadding=\"0\" cellspacing=\"0\" style=\"width:100%;")
                    .append("border-collapse:collapse;border:1px solid ").append(BORDER).append(";font-size:15px;\">");
            head.forEach(r -> row(sb, r, false));
            for (int i = 0; i < rows.size(); i++) {
                row(sb, rows.get(i), i == 0 && !head.isEmpty());
            }
            sb.append("</table>");
            link(sb, linkLabel, linkUrl, 28);
            moreLinks.forEach(l -> link(sb, l.label(), l.url(), 16));
            return sb.append("</div></body></html>").toString();
        }

        private static void link(StringBuilder sb, String label, String url, int marginTop) {
            sb.append("<p style=\"margin:").append(marginTop).append("px 0 4px;font-size:15px;\"><a href=\"")
                    .append(escape(url)).append("\" style=\"color:#2563eb;text-decoration:underline;\">")
                    .append(escape(label)).append("</a></p>")
                    .append("<p style=\"margin:0;font-size:13px;color:#6b7280;word-break:break-all;\">")
                    .append(escape(url)).append("</p>");
        }

        private static void row(StringBuilder sb, Row r, boolean divider) {
            String top = divider ? "border-top:2px solid #cbd5e1;" : "";
            sb.append("<tr><th scope=\"row\" style=\"width:28%;padding:12px 16px;background:#f3f4f6;border:1px solid ")
                    .append(BORDER).append(";").append(top)
                    .append("text-align:left;font-weight:400;color:#4b5563;white-space:nowrap;\">")
                    .append(escape(r.label())).append("</th>")
                    .append("<td style=\"padding:12px 16px;border:1px solid ").append(BORDER).append(";").append(top)
                    .append(r.strong() ? "font-weight:700;color:#111827;" : "color:#1f2937;").append("\">")
                    .append(escape(r.value()).replace("\n", "<br>")).append("</td></tr>");
        }
    }

    static String escape(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&#39;");
    }

    static String url(String baseUrl, String path) {
        String base = baseUrl == null ? "" : baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        return base + path;
    }
}
