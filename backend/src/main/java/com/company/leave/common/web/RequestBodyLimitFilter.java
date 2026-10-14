package com.company.leave.common.web;

import com.company.leave.common.dto.ApiResponse;
import com.company.leave.common.exception.ErrorCode;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectMapper;

/**
 * 파일 업로드(multipart)가 아닌 요청 본문은 1MB 까지만 받는다. 넘으면 413 {@link ErrorCode#REQUEST_TOO_LARGE}.
 * <ul>
 *   <li>Content-Length 가 1MB 를 넘으면 본문을 읽기 전에 거절한다</li>
 *   <li>길이를 미리 알리지 않는 요청(chunked)은 읽으면서 세다가 1MB 를 넘는 순간 {@link TooLargeException} 으로 끊는다.
 *       읽는 쪽(JSON 변환 등)에서 난 오류는 GlobalExceptionHandler 가 같은 413 으로 바꾼다</li>
 *   <li>엑셀 업로드(multipart)는 spring.servlet.multipart 의 5MB/8MB 제한을 그대로 따른다</li>
 * </ul>
 * 로그인 전 요청(로그인·비밀번호 찾기)에도 걸리도록 {@code EncodedPathRejectFilter} 바로 다음, 세션·보안 필터보다 앞에 둔다.
 * 화면이 보내는 JSON 은 길어야 수십 KB 라 1MB 는 넉넉하다.
 */
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
@Component
public class RequestBodyLimitFilter extends OncePerRequestFilter {

    /** multipart 가 아닌 요청 본문 최대 크기 */
    public static final long MAX_BODY_BYTES = 1024 * 1024;

    private final ObjectMapper objectMapper;

    public RequestBodyLimitFilter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain)
            throws ServletException, IOException {
        if (isMultipart(request)) {
            filterChain.doFilter(request, response);
            return;
        }
        if (request.getContentLengthLong() > MAX_BODY_BYTES) {
            writeTooLarge(response);
            return;
        }
        filterChain.doFilter(new LimitedRequest(request), response);
    }

    private static boolean isMultipart(HttpServletRequest request) {
        String type = request.getContentType();
        return type != null && type.toLowerCase(Locale.ROOT).startsWith("multipart/");
    }

    private void writeTooLarge(HttpServletResponse response) throws IOException {
        ErrorCode code = ErrorCode.REQUEST_TOO_LARGE;
        response.setStatus(code.status().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getWriter(), ApiResponse.error(code.name(), code.defaultMessage()));
    }

    /** 원인 사슬에 본문 크기 초과가 있는지(JSON 변환 등이 감싼 경우 포함) */
    public static boolean isTooLarge(Throwable ex) {
        for (Throwable t = ex; t != null; t = t.getCause()) {
            if (t instanceof TooLargeException) {
                return true;
            }
            if (t.getCause() == t) {
                break;
            }
        }
        return false;
    }

    /** 읽은 본문이 {@link #MAX_BODY_BYTES} 를 넘음 */
    public static class TooLargeException extends IOException {
        TooLargeException() {
            super("요청 본문이 " + MAX_BODY_BYTES + "바이트를 넘었습니다.");
        }
    }

    /** 본문을 읽을 때 크기를 세는 요청 */
    private static class LimitedRequest extends HttpServletRequestWrapper {

        private LimitedInputStream stream;

        LimitedRequest(HttpServletRequest request) {
            super(request);
        }

        @Override
        public ServletInputStream getInputStream() throws IOException {
            if (stream == null) {
                stream = new LimitedInputStream(super.getInputStream());
            }
            return stream;
        }

        @Override
        public BufferedReader getReader() throws IOException {
            String encoding = getCharacterEncoding();
            Charset charset = encoding != null ? Charset.forName(encoding) : StandardCharsets.UTF_8;
            return new BufferedReader(new InputStreamReader(getInputStream(), charset));
        }
    }

    private static class LimitedInputStream extends ServletInputStream {

        private final ServletInputStream delegate;
        private long count;

        LimitedInputStream(ServletInputStream delegate) {
            this.delegate = delegate;
        }

        @Override
        public int read() throws IOException {
            int b = delegate.read();
            if (b >= 0) {
                add(1);
            }
            return b;
        }

        @Override
        public int read(byte[] buf, int off, int len) throws IOException {
            int n = delegate.read(buf, off, len);
            if (n > 0) {
                add(n);
            }
            return n;
        }

        private void add(long n) throws TooLargeException {
            count += n;
            if (count > MAX_BODY_BYTES) {
                throw new TooLargeException();
            }
        }

        @Override
        public boolean isFinished() {
            return delegate.isFinished();
        }

        @Override
        public boolean isReady() {
            return delegate.isReady();
        }

        @Override
        public void setReadListener(ReadListener listener) {
            delegate.setReadListener(listener);
        }
    }
}
