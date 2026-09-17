package com.rizenfood.api.admin;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.rizenfood.api.common.NotFoundException;
import com.rizenfood.api.member.PasswordPolicy;

/**
 * 관리자 계정 관리.
 *
 *  - 누구나: 내 비밀번호 변경 (현재 비밀번호 확인 필수)
 *  - 최고관리자만: 계정 추가, 이름·권한·사용 여부 변경, 다른 관리자 비밀번호 초기화
 *
 * 스스로를 잠그는 사고를 막는다 — 자기 권한을 내리거나 자기 계정을 중지할 수 없고,
 * 마지막 남은 최고관리자는 내리거나 중지할 수 없다.
 * 비밀번호·권한·사용 여부가 바뀌면 그 계정의 기존 로그인은 모두 끊긴다(AdminUser.revokeSessions).
 */
@Service
public class AdminAccountService {

    static final String SUPER_ADMIN = "SUPER_ADMIN";
    private static final Set<String> ROLES = Set.of("ADMIN", SUPER_ADMIN);
    /** 영문 소문자·숫자로 시작, 4~30자. 로그인 아이디라 헷갈리는 문자를 받지 않는다. */
    private static final String USERNAME_PATTERN = "^[a-z0-9][a-z0-9._-]{3,29}$";

    private final AdminUserRepository repository;
    private final PasswordEncoder passwordEncoder;
    private final PasswordPolicy passwordPolicy;

    public AdminAccountService(AdminUserRepository repository, PasswordEncoder passwordEncoder,
                               PasswordPolicy passwordPolicy) {
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
        this.passwordPolicy = passwordPolicy;
    }

    public record View(Long id, String username, String displayName, String role,
                       boolean enabled, boolean locked, Instant lastLoginAt, Instant createdAt) {

        static View of(AdminUser a) {
            return new View(a.getId(), a.getUsername(), a.getDisplayName(), a.getRole(),
                    a.isEnabled(), a.isLocked(), a.getLastLoginAt(), a.getCreatedAt());
        }
    }

    // ── 내 계정 ──────────────────────────────────────────────

    @Transactional
    public AdminUser changeOwnPassword(Long adminId, String currentPassword, String newPassword) {
        AdminUser admin = find(adminId);
        if (currentPassword == null || !passwordEncoder.matches(currentPassword, admin.getPasswordHash())) {
            throw new IllegalArgumentException("현재 비밀번호가 맞지 않습니다.");
        }
        if (passwordEncoder.matches(newPassword, admin.getPasswordHash())) {
            throw new IllegalArgumentException("지금 쓰는 비밀번호와 다른 비밀번호를 입력해 주세요.");
        }
        requireStrong(newPassword);
        admin.changePassword(passwordEncoder.encode(newPassword));
        return admin;
    }

    /** 로그아웃. 이 계정으로 나간 토큰(다른 기기 포함)을 모두 끊는다. */
    @Transactional
    public void logout(Long adminId) {
        repository.findById(adminId).ifPresent(AdminUser::revokeSessions);
    }

    // ── 최고관리자 ───────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<View> list() {
        return repository.findAll(Sort.by("id")).stream().map(View::of).toList();
    }

    @Transactional
    public View create(String username, String displayName, String password, String role) {
        String name = normalizeUsername(username);
        if (repository.existsByUsername(name)) {
            throw new IllegalArgumentException("이미 쓰고 있는 아이디입니다.");
        }
        requireRole(role);
        requireStrong(password);
        AdminUser admin = new AdminUser(name, passwordEncoder.encode(password), cleanName(displayName), role);
        return View.of(repository.save(admin));
    }

    @Transactional
    public View update(Long actorId, Long targetId, String displayName, String role, boolean enabled) {
        AdminUser target = find(targetId);
        requireRole(role);

        boolean demoting = target.isSuperAdmin() && !SUPER_ADMIN.equals(role);
        boolean disabling = target.isEnabled() && !enabled;

        if (targetId.equals(actorId) && (demoting || disabling)) {
            throw new IllegalArgumentException("내 계정의 권한을 내리거나 사용 중지할 수 없습니다. 다른 최고관리자에게 요청해 주세요.");
        }
        if (target.isSuperAdmin() && target.isEnabled() && (demoting || disabling)
                && repository.countByRoleAndEnabledTrue(SUPER_ADMIN) <= 1) {
            throw new IllegalArgumentException("최고관리자가 한 명은 남아 있어야 합니다.");
        }

        target.rename(cleanName(displayName));
        target.changeRole(role);
        target.setEnabled(enabled);
        return View.of(target);
    }

    /** 다른 관리자의 비밀번호를 새로 정해 준다 (잊어버렸을 때). 잠금도 풀린다. */
    @Transactional
    public View resetPassword(Long actorId, Long targetId, String newPassword) {
        if (targetId.equals(actorId)) {
            throw new IllegalArgumentException("내 비밀번호는 '내 비밀번호 변경'에서 현재 비밀번호를 확인한 뒤 바꿔 주세요.");
        }
        AdminUser target = find(targetId);
        requireStrong(newPassword);
        target.changePassword(passwordEncoder.encode(newPassword));
        return View.of(target);
    }

    // ── 검사 ─────────────────────────────────────────────────

    private AdminUser find(Long id) {
        return repository.findById(id).orElseThrow(() -> new NotFoundException("관리자 계정을 찾을 수 없습니다."));
    }

    private void requireStrong(String password) {
        String problem = passwordPolicy.validate(password);
        if (problem != null) {
            throw new IllegalArgumentException(problem);
        }
    }

    private static void requireRole(String role) {
        if (role == null || !ROLES.contains(role)) {
            throw new IllegalArgumentException("권한을 선택해 주세요.");
        }
    }

    private static String normalizeUsername(String username) {
        String v = username == null ? "" : username.trim().toLowerCase();
        if (!v.matches(USERNAME_PATTERN)) {
            throw new IllegalArgumentException("아이디는 영문 소문자·숫자로 4~30자로 만들어 주세요. (. _ - 사용 가능)");
        }
        return v;
    }

    private static String cleanName(String displayName) {
        String v = displayName == null ? "" : displayName.trim();
        if (v.isEmpty() || v.length() > 30) {
            throw new IllegalArgumentException("이름은 1~30자로 입력해 주세요.");
        }
        return v;
    }
}
