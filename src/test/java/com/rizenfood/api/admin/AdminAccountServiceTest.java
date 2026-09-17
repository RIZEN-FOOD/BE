package com.rizenfood.api.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import com.rizenfood.api.member.PasswordPolicy;

/**
 * 관리자 계정 관리 규칙. 특히 "스스로를 잠그는 사고"를 막는지.
 */
class AdminAccountServiceTest {

    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(4);
    private AdminUserRepository repo;
    private AdminAccountService service;

    @BeforeEach
    void setUp() {
        repo = mock(AdminUserRepository.class);
        service = new AdminAccountService(repo, encoder, new PasswordPolicy());
        when(repo.save(any(AdminUser.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private AdminUser admin(long id, String role, String password) {
        AdminUser a = new AdminUser("user" + id, encoder.encode(password), "관리자" + id, role);
        ReflectionTestUtils.setField(a, "id", id);
        when(repo.findById(id)).thenReturn(Optional.of(a));
        return a;
    }

    @Test
    @DisplayName("내 비밀번호 변경: 현재 비밀번호가 틀리면 거부")
    void wrongCurrentPassword() {
        admin(1, "SUPER_ADMIN", "rizen2026ok");
        assertThatThrownBy(() -> service.changeOwnPassword(1L, "wrong", "newpass2026"))
                .hasMessageContaining("현재 비밀번호");
    }

    @Test
    @DisplayName("내 비밀번호 변경: 약한 비밀번호·같은 비밀번호는 거부")
    void weakOrSamePassword() {
        admin(1, "SUPER_ADMIN", "rizen2026ok");
        assertThatThrownBy(() -> service.changeOwnPassword(1L, "rizen2026ok", "123456"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.changeOwnPassword(1L, "rizen2026ok", "rizen2026ok"))
                .hasMessageContaining("다른 비밀번호");
    }

    @Test
    @DisplayName("내 비밀번호 변경: 성공하면 새 비밀번호로 바뀌고 기존 로그인은 끊긴다")
    void changePasswordRevokesSessions() {
        AdminUser a = admin(1, "SUPER_ADMIN", "rizen2026ok");
        int before = a.getTokenVersion();

        service.changeOwnPassword(1L, "rizen2026ok", "newpass2026");

        assertThat(encoder.matches("newpass2026", a.getPasswordHash())).isTrue();
        assertThat(a.getTokenVersion()).isGreaterThan(before);
    }

    @Test
    @DisplayName("내 권한을 내리거나 내 계정을 중지할 수 없다")
    void cannotLockOutSelf() {
        admin(1, "SUPER_ADMIN", "rizen2026ok");
        when(repo.countByRoleAndEnabledTrue("SUPER_ADMIN")).thenReturn(2L);

        assertThatThrownBy(() -> service.update(1L, 1L, "대표", "ADMIN", true))
                .hasMessageContaining("내 계정");
        assertThatThrownBy(() -> service.update(1L, 1L, "대표", "SUPER_ADMIN", false))
                .hasMessageContaining("내 계정");
    }

    @Test
    @DisplayName("마지막 최고관리자는 내리거나 중지할 수 없다")
    void lastSuperAdminIsProtected() {
        admin(1, "SUPER_ADMIN", "rizen2026ok");
        admin(2, "SUPER_ADMIN", "rizen2026ok");
        when(repo.countByRoleAndEnabledTrue("SUPER_ADMIN")).thenReturn(1L);

        assertThatThrownBy(() -> service.update(1L, 2L, "직원", "ADMIN", true))
                .hasMessageContaining("한 명은 남아");
    }

    @Test
    @DisplayName("다른 관리자 중지·권한 변경은 되고, 그 계정의 로그인이 끊긴다")
    void updateOtherRevokes() {
        admin(1, "SUPER_ADMIN", "rizen2026ok");
        AdminUser staff = admin(2, "ADMIN", "rizen2026ok");
        int before = staff.getTokenVersion();

        AdminAccountService.View v = service.update(1L, 2L, "직원 김", "ADMIN", false);

        assertThat(v.enabled()).isFalse();
        assertThat(v.displayName()).isEqualTo("직원 김");
        assertThat(staff.getTokenVersion()).isGreaterThan(before);
    }

    @Test
    @DisplayName("초기화로 내 비밀번호를 바꿀 수 없다 (현재 비밀번호 확인을 건너뛰게 되므로)")
    void cannotResetOwnPassword() {
        admin(1, "SUPER_ADMIN", "rizen2026ok");
        assertThatThrownBy(() -> service.resetPassword(1L, 1L, "newpass2026"))
                .hasMessageContaining("내 비밀번호");
    }

    @Test
    @DisplayName("계정 추가: 아이디 형식·중복·권한·비밀번호를 검사")
    void createValidates() {
        when(repo.existsByUsername("staff01")).thenReturn(true);

        assertThatThrownBy(() -> service.create("A!", "직원", "newpass2026", "ADMIN"))
                .hasMessageContaining("아이디");
        assertThatThrownBy(() -> service.create("staff01", "직원", "newpass2026", "ADMIN"))
                .hasMessageContaining("이미");
        assertThatThrownBy(() -> service.create("staff02", "직원", "newpass2026", "OWNER"))
                .hasMessageContaining("권한");
        assertThatThrownBy(() -> service.create("staff02", "직원", "short", "ADMIN"))
                .isInstanceOf(IllegalArgumentException.class);

        AdminAccountService.View created = service.create(" Staff02 ", "직원", "newpass2026", "ADMIN");
        assertThat(created.username()).isEqualTo("staff02");
        assertThat(created.role()).isEqualTo("ADMIN");
    }
}
