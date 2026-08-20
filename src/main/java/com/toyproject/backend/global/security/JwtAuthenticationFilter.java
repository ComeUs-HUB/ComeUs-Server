package com.toyproject.backend.global.security;

import com.toyproject.backend.domain.auth.exception.AuthErrorCode;
import com.toyproject.backend.domain.auth.token.JwtProvider;
import com.toyproject.backend.global.exception.ErrorCode;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * [6단계 · J] JWT 인증 필터
 *
 * <p><b>역할</b><br>
 * {@code Authorization: Bearer} 헤더를 해석해 {@code SecurityContext}를 채운다.
 * Phase 2 인증 흐름에서 가장 함정이 많은 지점이다.
 *
 * <p><b>설계</b>
 * <ul>
 *   <li>Bearer 토큰이 <b>없으면 그냥 통과</b>시킨다. permitAll 경로(authorize·callback·refresh·logout)가
 *       있으므로 여기서 막으면 로그인 자체가 불가능해진다.</li>
 *   <li>토큰이 있으면 파싱 → {@link CustomUserDetailsService} 조회 → {@code SecurityContextHolder} 설정.</li>
 *   <li><b>Bean으로 등록하지 않는다.</b> {@code SecurityConfig}가 {@code new}로 만들어 체인에 끼운다.
 *       Boot는 컨테이너에 있는 {@code Filter} 타입 Bean을 서블릿 필터 체인에도 자동 등록하는데,
 *       그러면 같은 인스턴스가 두 번 등록되어 {@code OncePerRequestFilter}의 중복 방지 속성 때문에
 *       <b>보안 체인에서의 실행이 건너뛰어진다</b>(서블릿 단계에서 먼저 실행되어 순서가 어긋난다).</li>
 * </ul>
 *
 * <p><b>핵심 설계 — 예외 시 short-circuit 하지 않는다</b><br>
 * 실패해도 여기서 응답을 쓰지 않는다. {@code SecurityContext}를 비우고 <b>사유만 request attribute에 남긴 뒤
 * 체인을 계속 진행</b>한다. 그러면 Spring Security가 미인증(anonymous)으로 판단해
 * {@link JwtAuthenticationEntryPoint}를 호출하고, EntryPoint가 attribute를 읽어 상태 코드를 결정한다.
 * 응답 작성 주체를 한 곳으로 모으기 위한 구조다.
 *
 * <table border="1">
 *   <caption>사유별 매핑</caption>
 *   <tr><th>상황</th><th>ErrorCode</th></tr>
 *   <tr><td>토큰 없음 / 사용자 미존재 등 기타</td><td>{@code UNAUTHORIZED}(A001, 401)</td></tr>
 *   <tr><td>서명 위조·형식 오류 ({@code JwtException})</td><td>{@code INVALID_TOKEN}(A003, 401)</td></tr>
 *   <tr><td>만료 ({@code ExpiredJwtException})</td><td>{@code EXPIRED_TOKEN}(A004, 401)</td></tr>
 *   <tr><td>탈퇴 회원 ({@code DisabledException})</td><td><b>{@code WITHDRAWN_USER}(A008, 403)</b></td></tr>
 * </table>
 *
 * <p><b>주의</b>
 * <ul>
 *   <li>{@code ExpiredJwtException}은 {@code JwtException}의 하위 타입이므로 <b>catch 순서가 중요하다.</b>
 *       {@code JwtException}을 먼저 잡으면 A004가 A003으로 뭉개진다(아래 catch 순서를 뒤집지 말 것).</li>
 *   <li>탈퇴 사유를 attribute로 넘기지 않으면 A008은 절대 반환되지 않는다(전부 A001이 된다).</li>
 * </ul>
 */
@Slf4j
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    /**
     * 인증 실패 사유를 담는 request attribute 키. {@link JwtAuthenticationEntryPoint}가 같은 키를 읽는다.
     *
     * <p>클래스명을 접두사로 써서 다른 컴포넌트의 attribute와 충돌하지 않게 한다.
     */
    public static final String ERROR_CODE_ATTRIBUTE = JwtAuthenticationFilter.class.getName() + ".ERROR_CODE";

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtProvider jwtProvider;
    private final CustomUserDetailsService userDetailsService;

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {
        String token = resolveToken(request);

        if (token != null) {
            try {
                authenticate(request, token);
            } catch (ExpiredJwtException e) {
                // ⚠️ ExpiredJwtException은 JwtException의 하위 타입이다. 반드시 먼저 잡아야 A004가 살아남는다.
                reject(request, AuthErrorCode.EXPIRED_TOKEN, e);
            } catch (JwtException | IllegalArgumentException e) {
                reject(request, AuthErrorCode.INVALID_TOKEN, e);
            } catch (DisabledException e) {
                reject(request, AuthErrorCode.WITHDRAWN_USER, e);
            } catch (UsernameNotFoundException e) {
                reject(request, AuthErrorCode.UNAUTHORIZED, e);
            }
        }
        // 성공·실패 무관하게 항상 체인을 계속 진행한다. 응답은 EntryPoint가 쓴다.
        filterChain.doFilter(request, response);
    }

    /** 파싱 → 조회 → {@code SecurityContext} 설정. 실패는 전부 호출부가 사유별로 잡는다. */
    private void authenticate(HttpServletRequest request, String token) {
        Claims claims = jwtProvider.parseClaims(token);
        UserDetails principal = userDetailsService.loadUserByUsername(claims.getSubject());

        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    /**
     * {@code Authorization: Bearer {token}}에서 토큰을 꺼낸다.
     *
     * <p>헤더가 없거나 {@code Bearer} 스킴이 아니면 {@code null}을 반환해 <b>익명 통과</b>시킨다
     * — 여기서 예외를 던지면 permitAll 경로까지 막힌다.
     */
    private String resolveToken(HttpServletRequest request) {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);

        if (StringUtils.hasText(header) && header.startsWith(BEARER_PREFIX)) {
            String token = header.substring(BEARER_PREFIX.length()).trim();
            return StringUtils.hasText(token) ? token : null;
        }
        return null;
    }

    /**
     * 인증을 포기하고 사유만 남긴다. <b>응답을 쓰지 않는다.</b>
     *
     * <p>{@code SecurityContext}를 비우는 이유: 이전 요청의 잔여 컨텍스트가 남아 있으면
     * 실패한 요청이 인증된 것으로 통과할 수 있다.
     */
    private void reject(HttpServletRequest request, ErrorCode errorCode, Exception cause) {
        SecurityContextHolder.clearContext();
        request.setAttribute(ERROR_CODE_ATTRIBUTE, errorCode);
        log.debug("인증 실패 — code={}, reason={}", errorCode.getCode(), cause.getMessage());
    }
}
