package com.toyproject.backend.domain.auth.service;

import com.toyproject.backend.domain.auth.client.KakaoOAuthClient;
import com.toyproject.backend.domain.auth.client.dto.KakaoTokenResponse;
import com.toyproject.backend.domain.auth.client.dto.KakaoUserInfoResponse;
import com.toyproject.backend.domain.auth.entity.RefreshToken;
import com.toyproject.backend.domain.auth.repository.RefreshTokenRepository;
import com.toyproject.backend.domain.user.entity.Provider;
import com.toyproject.backend.domain.user.entity.User;
import com.toyproject.backend.domain.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willAnswer;
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
    private static final String KAKAO_EMAIL = "rollback@kakao.com";
    /** 같은 email을 선점하거나 같은 계정을 먼저 만드는 '경쟁자' */
    private static final String RIVAL_PROVIDER_ID = "8002";

    @Autowired
    private AuthService authService;

    /**
     * 조회와 저장 <b>사이</b>에 경쟁자를 끼워 넣기 위한 훅으로만 쓴다. 저장과 제약 위반은 실제 DB가 한다.
     * 경합은 밀리초 단위 창이라 스레드를 띄워서는 결정적으로 재현되지 않는다.
     */
    @MockitoSpyBean
    private UserRepository userRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

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

    /** 테스트 트랜잭션이 없으므로 커밋된 회원은 직접 지운다(경합 테스트가 만든 경쟁자 포함). */
    @AfterEach
    void tearDown() {
        for (String providerId : new String[]{String.valueOf(KAKAO_ID), RIVAL_PROVIDER_ID}) {
            userRepository.findByProviderAndProviderId(Provider.KAKAO, providerId)
                    .ifPresent(userRepository::delete);
        }
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

    /**
     * 경쟁자를 <b>별도 트랜잭션에서 즉시 커밋</b>한다. 로그인 트랜잭션이 열려 있는 동안
     * 남이 먼저 커밋하는 상황이 곧 이 경합이다.
     */
    private void commitRival(String providerId, String email) {
        TransactionTemplate rivalTx = new TransactionTemplate(transactionManager);
        rivalTx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        rivalTx.executeWithoutResult(status ->
                userRepository.save(User.ofKakao(providerId, email, "경쟁자")));
    }

    /**
     * 조회와 저장 사이에 경쟁자를 커밋시킨다.
     *
     * <p>{@code existsByEmail}은 {@code findOrCreateKakaoUser}가 <b>신규 생성 분기에서만</b>,
     * 그것도 조회 직후 저장 직전에 부르는 메서드라 끼어들 자리로 정확하다.
     * 반환값은 그 시점 DB 상태 그대로다 — 1차는 아직 못 본 상태({@code false}),
     * 2차는 경쟁자가 보이는 상태({@code true}).
     */
    private void givenRivalCommitsBetweenReadAndWrite(String rivalProviderId, String rivalEmail) {
        AtomicInteger calls = new AtomicInteger();
        willAnswer(invocation -> {
            if (calls.getAndIncrement() > 0) {
                return true;
            }
            commitRival(rivalProviderId, rivalEmail);
            return false;
        }).given(userRepository).existsByEmail(KAKAO_EMAIL);
    }

    @Test
    @DisplayName("가입 경합 — 같은 계정을 먼저 만든 쪽이 커밋되면 재시도가 그 회원으로 붙는다")
    void retriesOntoWinnerOnProviderConflict() {
        given(refreshTokenRepository.save(any(RefreshToken.class))).willAnswer(inv -> inv.getArgument(0));
        givenRivalCommitsBetweenReadAndWrite(String.valueOf(KAKAO_ID), "winner@kakao.com");

        boolean isNewUser = authService.kakaoLogin(CODE).isNewUser();

        assertThat(isNewUser)
                .as("uk_users_provider_provider_id 위반 후 재시도가 기존 회원을 찾아낸다")
                .isFalse();
        assertThat(userRepository.findByProviderAndProviderId(Provider.KAKAO, String.valueOf(KAKAO_ID)))
                .get()
                .extracting(User::getEmail)
                .as("중복 회원이 생기지 않고 먼저 커밋된 쪽이 그대로 쓰인다")
                .isEqualTo("winner@kakao.com");
    }

    @Test
    @DisplayName("email 경합 — 재시도에서 email만 비우고 가입을 성립시킨다")
    void retriesWithoutEmailOnEmailConflict() {
        given(refreshTokenRepository.save(any(RefreshToken.class))).willAnswer(inv -> inv.getArgument(0));
        givenRivalCommitsBetweenReadAndWrite(RIVAL_PROVIDER_ID, KAKAO_EMAIL);

        boolean isNewUser = authService.kakaoLogin(CODE).isNewUser();

        assertThat(isNewUser)
                .as("email 충돌은 가입을 막지 않는다 — 신규 가입은 그대로 성립한다")
                .isTrue();
        assertThat(userRepository.findByProviderAndProviderId(Provider.KAKAO, String.valueOf(KAKAO_ID)))
                .get()
                .extracting(User::getEmail)
                .as("uk_users_email 위반 후 재시도에서 adoptableEmail이 email을 비운다")
                .isNull();
    }
}
