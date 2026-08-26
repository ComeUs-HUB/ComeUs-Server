package com.toyproject.backend.domain.user.service;

import com.toyproject.backend.domain.auth.exception.AuthErrorCode;
import com.toyproject.backend.domain.user.dto.KakaoUserResolution;
import com.toyproject.backend.domain.user.dto.UserResponse;
import com.toyproject.backend.domain.user.dto.UserUpdateRequest;
import com.toyproject.backend.domain.user.entity.Provider;
import com.toyproject.backend.domain.user.entity.Role;
import com.toyproject.backend.domain.user.entity.User;
import com.toyproject.backend.domain.user.entity.UserStatus;
import com.toyproject.backend.domain.user.repository.UserRepository;
import com.toyproject.backend.global.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

/**
 * [7단계 · K] {@link UserService} 검증
 *
 * <p><b>Spring 컨텍스트를 띄우지 않는 순수 단위 테스트</b>다. 이 단계의 완료 조건은
 * "{@code findOrCreateKakaoUser} 3분기"라는 <b>분기 판단</b>이고, 그 판단은 DB 없이 전부 드러난다.
 * 실제 SQL(복합 유니크·중복 가입 차단)은 3단계 {@code UserRepositoryTest}가 이미 검증했다.
 *
 * <p>{@code save}를 스텁할 때 인자를 그대로 돌려주는 이유: 실제 JPA는 저장한 엔티티를 반환하는데,
 * 여기서 다른 객체를 돌려주면 "어떤 값으로 회원을 만들었는가"를 검증할 수 없다.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("UserService")
class UserServiceTest {

    private static final String PROVIDER_ID = "kakao-1001";
    private static final String EMAIL = "user@kakao.com";
    private static final String NICKNAME = "민준";
    private static final Long USER_ID = 1L;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private UserService userService;

    @Nested
    @DisplayName("findOrCreateKakaoUser — 3분기")
    class FindOrCreateKakaoUser {

        @Test
        @DisplayName("ACTIVE 회원이면 그대로 재사용하고 새로 만들지 않는다")
        void reusesActiveUser() {
            User existing = User.ofKakao(PROVIDER_ID, EMAIL, NICKNAME);
            given(userRepository.findByProviderAndProviderId(Provider.KAKAO, PROVIDER_ID))
                    .willReturn(Optional.of(existing));

            KakaoUserResolution result = userService.findOrCreateKakaoUser(PROVIDER_ID, EMAIL, NICKNAME);

            assertThat(result.user()).isSameAs(existing);
            assertThat(result.isNewUser()).isFalse();
            then(userRepository).should(never()).save(any());
        }

        @Test
        @DisplayName("WITHDRAWN 회원이면 재활성화한다 — 기존 회원이므로 isNewUser는 false다")
        void reactivatesWithdrawnUser() {
            User withdrawn = User.ofKakao(PROVIDER_ID, EMAIL, NICKNAME);
            withdrawn.withdraw();
            given(userRepository.findByProviderAndProviderId(Provider.KAKAO, PROVIDER_ID))
                    .willReturn(Optional.of(withdrawn));

            KakaoUserResolution result = userService.findOrCreateKakaoUser(PROVIDER_ID, EMAIL, NICKNAME);

            assertThat(result.user().getStatus()).isEqualTo(UserStatus.ACTIVE);
            assertThat(result.user().getWithdrawnAt()).isNull();
            assertThat(result.isNewUser())
                    .as("재활성화는 가입이 아니라 복귀다 — FE의 '추가 정보 입력' UX가 다시 뜨면 안 된다")
                    .isFalse();
            then(userRepository).should(never()).save(any());
        }

        @Test
        @DisplayName("가입 이력이 없으면 KAKAO 회원을 새로 만든다")
        void createsNewUser() {
            given(userRepository.findByProviderAndProviderId(Provider.KAKAO, PROVIDER_ID))
                    .willReturn(Optional.empty());
            given(userRepository.existsByEmail(EMAIL)).willReturn(false);
            given(userRepository.save(any(User.class))).willAnswer(invocation -> invocation.getArgument(0));

            KakaoUserResolution result = userService.findOrCreateKakaoUser(PROVIDER_ID, EMAIL, NICKNAME);

            assertThat(result.isNewUser()).isTrue();
            assertThat(result.user().getProvider()).isEqualTo(Provider.KAKAO);
            assertThat(result.user().getProviderId()).isEqualTo(PROVIDER_ID);
            assertThat(result.user().getEmail()).isEqualTo(EMAIL);
            assertThat(result.user().getName()).isEqualTo(NICKNAME);
            assertThat(result.user().getRole()).isEqualTo(Role.USER);
            assertThat(result.user().getStatus()).isEqualTo(UserStatus.ACTIVE);
        }

        @Test
        @DisplayName("이메일이 이미 사용 중이면 email만 비우고 가입은 계속 진행한다")
        void dropsDuplicateEmailButStillSignsUp() {
            given(userRepository.findByProviderAndProviderId(Provider.KAKAO, PROVIDER_ID))
                    .willReturn(Optional.empty());
            given(userRepository.existsByEmail(EMAIL)).willReturn(true);
            given(userRepository.save(any(User.class))).willAnswer(invocation -> invocation.getArgument(0));

            KakaoUserResolution result = userService.findOrCreateKakaoUser(PROVIDER_ID, EMAIL, NICKNAME);

            assertThat(result.isNewUser())
                    .as("중복 이메일로 소셜 가입이 막히면 안 된다")
                    .isTrue();
            assertThat(result.user().getEmail()).isNull();
            assertThat(result.user().getName()).isEqualTo(NICKNAME);
        }

        @Test
        @DisplayName("이메일 미동의(null)면 중복 조회 자체를 하지 않는다")
        void skipsEmailLookupWhenEmailIsNull() {
            given(userRepository.findByProviderAndProviderId(Provider.KAKAO, PROVIDER_ID))
                    .willReturn(Optional.empty());
            given(userRepository.save(any(User.class))).willAnswer(invocation -> invocation.getArgument(0));

            KakaoUserResolution result = userService.findOrCreateKakaoUser(PROVIDER_ID, null, NICKNAME);

            assertThat(result.user().getEmail()).isNull();
            then(userRepository)
                    .should(never())
                    .existsByEmail(anyString());
        }
    }

    @Nested
    @DisplayName("updateMyInfo — null은 '변경하지 않음'이다")
    class UpdateMyInfo {

        @Test
        @DisplayName("보낸 필드만 바뀌고 나머지는 그대로다")
        void updatesOnlyProvidedFields() {
            User user = User.ofKakao(PROVIDER_ID, EMAIL, NICKNAME);
            user.updateProfile(null, "010-1111-2222", "서울시 강남구", LocalDate.of(1999, 1, 1));
            given(userRepository.findById(USER_ID)).willReturn(Optional.of(user));

            UserResponse response = userService.updateMyInfo(USER_ID,
                    new UserUpdateRequest("지훈", null, null, null));

            assertThat(response.name()).isEqualTo("지훈");
            assertThat(response.phone())
                    .as("보내지 않은 필드가 null로 덮이면 부분 수정이 성립하지 않는다")
                    .isEqualTo("010-1111-2222");
            assertThat(response.address()).isEqualTo("서울시 강남구");
            assertThat(response.birthDate()).isEqualTo(LocalDate.of(1999, 1, 1));
        }

        @Test
        @DisplayName("빈 요청이면 아무것도 바뀌지 않는다")
        void emptyRequestChangesNothing() {
            User user = User.ofKakao(PROVIDER_ID, EMAIL, NICKNAME);
            given(userRepository.findById(USER_ID)).willReturn(Optional.of(user));

            UserResponse response = userService.updateMyInfo(USER_ID,
                    new UserUpdateRequest(null, null, null, null));

            assertThat(response.name()).isEqualTo(NICKNAME);
        }

        @Test
        @DisplayName("더티 체킹으로 반영한다 — save를 부르지 않는다")
        void reliesOnDirtyChecking() {
            given(userRepository.findById(USER_ID))
                    .willReturn(Optional.of(User.ofKakao(PROVIDER_ID, EMAIL, NICKNAME)));

            userService.updateMyInfo(USER_ID, new UserUpdateRequest("지훈", null, null, null));

            then(userRepository).should(never()).save(any());
        }

        @Test
        @DisplayName("탈퇴 회원이면 A008 — getActiveUser의 규칙을 그대로 따른다")
        void rejectsWithdrawnUser() {
            User withdrawn = User.ofKakao(PROVIDER_ID, EMAIL, NICKNAME);
            withdrawn.withdraw();
            given(userRepository.findById(USER_ID)).willReturn(Optional.of(withdrawn));

            assertThatThrownBy(() -> userService.updateMyInfo(USER_ID,
                    new UserUpdateRequest("지훈", null, null, null)))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(AuthErrorCode.WITHDRAWN_USER);
        }
    }

    @Nested
    @DisplayName("getMyInfo")
    class GetMyInfo {

        @Test
        @DisplayName("응답에 providerId를 담지 않는다 — 외부 서비스의 식별자다")
        void doesNotExposeProviderId() {
            given(userRepository.findById(USER_ID))
                    .willReturn(Optional.of(User.ofKakao(PROVIDER_ID, EMAIL, NICKNAME)));

            UserResponse response = userService.getMyInfo(USER_ID);

            assertThat(response.email()).isEqualTo(EMAIL);
            assertThat(response.name()).isEqualTo(NICKNAME);
            assertThat(UserResponse.class.getRecordComponents())
                    .extracting(java.lang.reflect.RecordComponent::getName)
                    .doesNotContain("providerId", "status", "withdrawnAt");
        }
    }

    @Nested
    @DisplayName("withdraw")
    class Withdraw {

        @Test
        @DisplayName("소프트 삭제만 하고 카카오 unlink에 쓸 providerId를 돌려준다")
        void softDeletesAndReturnsProviderId() {
            User user = User.ofKakao(PROVIDER_ID, EMAIL, NICKNAME);
            given(userRepository.findById(USER_ID)).willReturn(Optional.of(user));

            String providerId = userService.withdraw(USER_ID);

            assertThat(providerId).isEqualTo(PROVIDER_ID);
            assertThat(user.getStatus()).isEqualTo(UserStatus.WITHDRAWN);
            assertThat(user.getWithdrawnAt()).isNotNull();
            then(userRepository).should(never()).delete(any());
        }

        @Test
        @DisplayName("회원이 없으면 A001 — 인증 통과 후 소실된 경합이라 401로 재로그인을 유도한다")
        void throwsUnauthorizedWhenUserIsGone() {
            given(userRepository.findById(USER_ID)).willReturn(Optional.empty());

            assertThatThrownBy(() -> userService.withdraw(USER_ID))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(AuthErrorCode.UNAUTHORIZED);
        }

        @Test
        @DisplayName("이미 탈퇴한 회원이면 A008(403) — 필터와 같은 규칙으로 사유를 구분한다")
        void throwsWithdrawnUserWhenAlreadyWithdrawn() {
            User withdrawn = User.ofKakao(PROVIDER_ID, EMAIL, NICKNAME);
            withdrawn.withdraw();
            LocalDateTime withdrawnAt = withdrawn.getWithdrawnAt();
            given(userRepository.findById(USER_ID)).willReturn(Optional.of(withdrawn));

            assertThatThrownBy(() -> userService.withdraw(USER_ID))
                    .as("A001로 뭉개면 탈퇴 더블 클릭에서 같은 상태가 401/403으로 흔들린다")
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(AuthErrorCode.WITHDRAWN_USER);

            assertThat(withdrawn.getWithdrawnAt())
                    .as("두 번째 호출이 withdraw()를 다시 불러 탈퇴 시각을 덮어쓰면 안 된다")
                    .isEqualTo(withdrawnAt);
        }
    }
}
