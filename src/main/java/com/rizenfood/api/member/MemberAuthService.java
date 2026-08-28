package com.rizenfood.api.member;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.rizenfood.api.security.JwtProperties;

/**
 * 회원 인증의 핵심 로직 (기획서 §6).
 *
 * 지키는 것:
 *  - 비밀번호는 BCrypt 해시로만 저장 (PasswordEncoder)
 *  - 로그인 실패 사유를 구분해 알려주지 않음 (계정 존재 여부 은폐)
 *  - 5회 실패 시 10분 잠금 — 실패 기록은 별도 트랜잭션 (롤백 방지)
 *  - 리프레시 토큰은 해시로 저장, 로그인·로그아웃 시 회전·무효화
 *  - 동의 시각 기록, 탈퇴 시 개인정보 파기 + purge_at
 */
@Service
public class MemberAuthService {

    private static final String LOCAL = "LOCAL";
    /** 탈퇴 후에도 거래기록 등으로 최소 보존하는 기간 */
    private static final int RETENTION_DAYS = 30;
    private static final String DUMMY_HASH =
            "$2a$10$7EqJtq98hPqEX7fNZaFWoOa9rB4gVBoT0aXaJqDLmT0dCkG0K8QOe";

    private final MemberRepository memberRepo;
    private final RefreshTokenRepository refreshRepo;
    private final MemberLoginAttemptService attemptService;
    private final PasswordEncoder passwordEncoder;
    private final PasswordPolicy passwordPolicy;
    private final PhoneCipher phoneCipher;
    private final JwtProperties jwtProperties;
    private final SecureRandom random = new SecureRandom();

    public MemberAuthService(MemberRepository memberRepo, RefreshTokenRepository refreshRepo,
                             MemberLoginAttemptService attemptService, PasswordEncoder passwordEncoder,
                             PasswordPolicy passwordPolicy, PhoneCipher phoneCipher,
                             JwtProperties jwtProperties) {
        this.memberRepo = memberRepo;
        this.refreshRepo = refreshRepo;
        this.attemptService = attemptService;
        this.passwordEncoder = passwordEncoder;
        this.passwordPolicy = passwordPolicy;
        this.phoneCipher = phoneCipher;
        this.jwtProperties = jwtProperties;
    }

    // ── 가입 ─────────────────────────────────────────────────

    public boolean isEmailAvailable(String email) {
        return !memberRepo.existsByEmailAndProvider(normalize(email), LOCAL);
    }

    @Transactional
    public Member signup(String email, String rawPassword, String name,
                         String rawPhone, boolean marketingAgreed, boolean ageOver14) {
        String normalized = normalize(email);

        if (!ageOver14) {
            throw new MemberAuthException("만 14세 이상만 가입할 수 있습니다.");
        }
        if (memberRepo.existsByEmailAndProvider(normalized, LOCAL)) {
            throw new MemberAuthException("이미 가입된 이메일입니다.");
        }
        String pwProblem = passwordPolicy.validate(rawPassword);
        if (pwProblem != null) {
            throw new MemberAuthException(pwProblem);
        }

        Member member = Member.localMember(normalized, passwordEncoder.encode(rawPassword), name.trim());
        member.recordConsents(marketingAgreed, true);
        if (rawPhone != null && !rawPhone.isBlank()) {
            member.updatePhone(phoneCipher.encrypt(rawPhone.replaceAll("[^0-9]", "")));
        }
        return memberRepo.save(member);
    }

    // ── 로그인 ───────────────────────────────────────────────

    @Transactional
    public Member login(String email, String rawPassword) {
        Member member = memberRepo.findByEmailAndProvider(normalize(email), LOCAL).orElse(null);

        if (member == null || member.getPasswordHash() == null) {
            // 계정이 없거나 소셜 전용이어도 대조 시간을 맞춰 존재 여부를 숨긴다.
            passwordEncoder.matches(rawPassword, DUMMY_HASH);
            throw new MemberAuthException("이메일 또는 비밀번호가 올바르지 않습니다.");
        }
        if (member.isWithdrawn()) {
            throw new MemberAuthException("탈퇴한 계정입니다.");
        }
        if (member.isLocked()) {
            throw new MemberAuthException(
                    "로그인 시도가 너무 많습니다. " + member.lockRemainingMinutes() + "분 후에 다시 시도해 주세요.");
        }

        if (!passwordEncoder.matches(rawPassword, member.getPasswordHash())) {
            var result = attemptService.recordFailure(member.getId());
            if (result.locked()) {
                throw new MemberAuthException(
                        "로그인 시도가 너무 많습니다. " + Member.LOCK_MINUTES + "분 후에 다시 시도해 주세요.");
            }
            throw new MemberAuthException(
                    "이메일 또는 비밀번호가 올바르지 않습니다. " + result.attemptsLeft() + "회 더 틀리면 잠깁니다.");
        }

        attemptService.recordSuccess(member.getId());
        return member;
    }

    // ── 리프레시 토큰 ─────────────────────────────────────────

    /**
     * 새 리프레시 토큰을 발급하고 DB 에 해시로 저장한다.
     * @return 쿠키에 담을 원문 토큰
     */
    @Transactional
    public String issueRefreshToken(Long memberId, String userAgent, String ip) {
        String raw = randomToken();
        Instant expiry = Instant.now().plusSeconds(jwtProperties.memberRefreshDays() * 86400L);
        refreshRepo.save(new RefreshToken(memberId, sha256(raw), expiry, userAgent, ip));
        return raw;
    }

    /**
     * 리프레시 토큰을 회전한다. 기존 것을 무효화하고 새 것을 발급한다.
     * 재사용(이미 무효화된 토큰으로 재요청)은 거부한다.
     * @return [새 refresh 원문, memberId]
     */
    @Transactional
    public RefreshResult rotate(String rawRefresh, String userAgent, String ip) {
        if (rawRefresh == null || rawRefresh.isBlank()) {
            throw new MemberAuthException("로그인이 필요합니다.");
        }
        RefreshToken token = refreshRepo.findByTokenHash(sha256(rawRefresh))
                .orElseThrow(() -> new MemberAuthException("로그인이 필요합니다."));

        if (!token.isUsable()) {
            throw new MemberAuthException("세션이 만료되었습니다. 다시 로그인해 주세요.");
        }

        token.revoke();
        String newRaw = issueRefreshToken(token.getMemberId(), userAgent, ip);
        return new RefreshResult(newRaw, token.getMemberId());
    }

    @Transactional
    public void revokeAll(Long memberId) {
        refreshRepo.revokeAllByMember(memberId, Instant.now());
    }

    // ── 탈퇴 ─────────────────────────────────────────────────

    @Transactional
    public void withdraw(Long memberId) {
        Member member = memberRepo.findById(memberId)
                .orElseThrow(() -> new MemberAuthException("회원을 찾을 수 없습니다."));
        member.withdraw(RETENTION_DAYS);
        refreshRepo.revokeAllByMember(memberId, Instant.now());
    }

    @Transactional(readOnly = true)
    public Member get(Long memberId) {
        return memberRepo.findById(memberId)
                .orElseThrow(() -> new MemberAuthException("회원을 찾을 수 없습니다."));
    }

    // ── 유틸 ─────────────────────────────────────────────────

    private String normalize(String email) {
        return email == null ? "" : email.trim().toLowerCase();
    }

    private String randomToken() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String sha256(String value) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(value.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (Exception e) {
            throw new IllegalStateException("토큰 해시 계산 실패", e);
        }
    }

    public record RefreshResult(String newRefreshRaw, Long memberId) {
    }

    /**
     * 로그인 실패·성공 기록을 별도 트랜잭션으로 처리한다.
     * 관리자 인증과 같은 이유 — 실패는 예외로 알리는데, 같은 트랜잭션에서
     * 기록하면 그 예외가 실패 횟수 증가까지 롤백시켜 잠금이 안 걸린다.
     */
    @Service
    public static class MemberLoginAttemptService {
        private final MemberRepository repo;

        public MemberLoginAttemptService(MemberRepository repo) {
            this.repo = repo;
        }

        @Transactional(propagation = Propagation.REQUIRES_NEW)
        public FailureResult recordFailure(Long memberId) {
            Member m = repo.findById(memberId).orElseThrow();
            m.recordLoginFailure();
            repo.saveAndFlush(m);
            return new FailureResult(m.isLocked(),
                    Math.max(0, Member.MAX_FAILED_ATTEMPTS - m.getFailedCount()));
        }

        @Transactional(propagation = Propagation.REQUIRES_NEW)
        public void recordSuccess(Long memberId) {
            Member m = repo.findById(memberId).orElseThrow();
            m.recordLoginSuccess();
            repo.saveAndFlush(m);
        }

        public record FailureResult(boolean locked, int attemptsLeft) {
        }
    }
}
