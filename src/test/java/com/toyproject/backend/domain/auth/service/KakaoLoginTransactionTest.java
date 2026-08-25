package com.toyproject.backend.domain.auth.service;

import com.toyproject.backend.domain.auth.client.KakaoOAuthClient;
import com.toyproject.backend.domain.auth.client.dto.KakaoTokenResponse;
import com.toyproject.backend.domain.auth.client.dto.KakaoUserInfoResponse;
import com.toyproject.backend.domain.auth.entity.RefreshToken;
import com.toyproject.backend.domain.auth.repository.RefreshTokenRepository;
import com.toyproject.backend.domain.user.entity.Provider;
import com.toyproject.backend.domain.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;

/**
 * [8단계 · I·K] 로그인 DB 구간의 원자성 검증
 *
 * <p><b>{@code @Transactional}을 붙이지 않는다.</b> 테스트 트랜잭션으로 감싸면 안쪽 트랜잭션이
 * 거기에 참여해 <b>롤백 여부를 구분할 수 없다.</b> 실제 커밋/롤백을 보려면 감싸지 않아야 하고,
 * 그 대가로 뒷정리를 {@link #tearDown()}에서 직접 한다.
 *
 * <p>커넥션 타임아웃 자체를 재현하지는 않는다. {@code RefreshTokenRepository}를 대체해
 * {@code save}에서 같은 계열의 예외를 던지게 하고, <b>회원 생성이 함께 롤백되는지만</b> 본다 —
 * 그게 이 설계가 보장해야 하는 것이다.
 *
 * <p>왜 회원까지 롤백되어야 하는가: 회원만 남으면 재시도 시 <b>기존 회원</b>으로 잡혀
 * {@code isNewUser=false}가 된다. 로그인은 복구되지만 FE의 "추가 정보 입력" UX는 영영 뜨지 않는다.
 */
@SpringBootTest
@DisplayName("카카오 로그인 트랜잭션 (8단계)")
class KakaoLoginTransactionTest {

    private static final String CODE = "kakao-authorization-code";
    private static final String KAKAO_ACCESS_TOKEN = "kakao-access-token";
    private static final Long KAKAO_ID = 8_001L;

    @Autowired
    private AuthService authService;

    @Autowired
    private UserRepository userRepository;

    @MockitoBean
    private KakaoOAuthClient kakaoOAuthClient;

    @MockitoBean
    private RefreshTokenRepository refreshTokenRepository;

    @BeforeEach
    void setUp() {
        given(kakaoOAuthClient.requestToken(CODE))
                .willReturn(new KakaoTokenResponse(KAKAO_ACCESS_TOKEN, "bearer", null, 21_599, null));
        given(kakaoOAuthClient.fetchUserInfo(KAKAO_ACCESS_TOKEN))
                .willReturn(new KakaoUserInfoResponse(
                        KAKAO_ID,
                        new KakaoUserInfoResponse.KakaoAccount(
                                "rollback@kakao.com", new KakaoUserInfoResponse.Profile("민준"))));
    }

    /** 테스트 트랜잭션이 없으므로 커밋된 회원은 직접 지운다. */
    @AfterEach
    void tearDown() {
        userRepository.findByProviderAndProviderId(Provider.KAKAO, String.valueOf(KAKAO_ID))
                .ifPresent(userRepository::delete);
    }

    @Test
    @DisplayName("토큰 저장이 실패하면 회원 생성까지 함께 롤백된다")
    void rollsBackUserCreationWhenTokenPersistenceFails() {
        willThrow(new DataAccessResourceFailureException("커넥션 타임아웃"))
                .given(refreshTokenRepository).save(any(RefreshToken.class));

        assertThatThrownBy(() -> authService.kakaoLogin(CODE))
                .isInstanceOf(DataAccessResourceFailureException.class);

        assertThat(userRepository.findByProviderAndProviderId(Provider.KAKAO, String.valueOf(KAKAO_ID)))
                .as("회원만 남으면 재시도 때 isNewUser=false가 되어 '추가 정보 입력' UX를 잃는다")
                .isEmpty();
    }

    @Test
    @DisplayName("정상 흐름에서는 회원이 커밋되고 신규 가입으로 표시된다")
    void commitsUserOnSuccess() {
        given(refreshTokenRepository.save(any(RefreshToken.class))).willAnswer(inv -> inv.getArgument(0));

        boolean isNewUser = authService.kakaoLogin(CODE).isNewUser();

        assertThat(isNewUser).isTrue();
        assertThat(userRepository.findByProviderAndProviderId(Provider.KAKAO, String.valueOf(KAKAO_ID)))
                .as("롤백 테스트가 성공 경로까지 막아버리지 않았는지 확인한다")
                .isPresent();
    }
}
