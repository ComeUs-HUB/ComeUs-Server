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
 * [Phase 2 · 6단계] Bearer 토큰을 해석해 {@code SecurityContext}를 채운다.
 *
 * <p>실패해도 응답을 쓰지 않는다. 사유만 request attribute에 남기고 체인을 계속 진행해
 * {@code JwtAuthenticationEntryPoint}가 상태 코드를 정한다.
 * <p>Bean으로 등록하지 않는다. {@code Filter} 타입 Bean은 서블릿 체인에도 자동 등록돼
 * {@code OncePerRequestFilter}의 중복 방지 때문에 보안 체인 실행이 건너뛰어진다.
 * <p>계약은 {@code docs/domains/auth.md}의 "인증 필터".
 */
@Slf4j
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    /** 인증 실패 사유를 담는 attribute 키. 클래스명 접두사로 충돌을 피한다. */
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
                // ExpiredJwtException은 JwtException의 하위 타입이다.
                // 순서를 뒤집으면 A004가 A003으로 뭉개진다.
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
     * <p>헤더가 없거나 {@code Bearer} 스킴이 아니면 {@code null}을 반환해 익명 통과시킨다
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
     * 인증을 포기하고 사유만 남긴다. 응답을 쓰지 않는다.
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
