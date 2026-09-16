package com.rizenfood.api.member.social;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.rizenfood.api.member.Member;
import com.rizenfood.api.member.MemberAuthException;
import com.rizenfood.api.member.MemberRepository;

/**
 * 간편 로그인(카카오·네이버)으로 들어온 사람을 우리 회원과 이어준다.
 *
 * 판단 순서
 *  1) 제공자 고유번호로 이미 가입한 회원 → 로그인
 *  2) 제공자가 확인해 준 이메일이 다른 계정(이메일 가입 등)에 이미 있음 → 막고 기존 방식 안내
 *     (자동으로 합치지 않는다. 남의 이메일로 만든 소셜 계정이 기존 계정을 가로채는 사고를 원천 차단)
 *  3) 처음 온 사람 → 약관·만 14세 동의를 받은 뒤 가입 (동의 없이 계정을 만들지 않는다)
 */
@Service
public class SocialLoginService {

    private static final int NAME_MAX = 100;

    private final List<SocialProviderClient> clients;
    private final MemberRepository memberRepo;
    private final SocialProperties properties;

    public SocialLoginService(List<SocialProviderClient> clients, MemberRepository memberRepo,
                              SocialProperties properties) {
        this.clients = List.copyOf(clients);
        this.memberRepo = memberRepo;
        this.properties = properties;
    }

    /** 제공자별 사용 가능 여부. 화면이 버튼을 띄울지 정한다. */
    public Map<String, Boolean> enabledProviders() {
        Map<String, Boolean> out = new LinkedHashMap<>();
        clients.forEach(client -> out.put(client.provider(), client.enabled()));
        return out;
    }

    /** 켜져 있는 제공자. 모르거나 꺼져 있으면 비어 있다. */
    public Optional<SocialProviderClient> client(String provider) {
        return clients.stream()
                .filter(c -> provider != null && provider.equals(c.provider()))
                .filter(SocialProviderClient::enabled)
                .findFirst();
    }

    public String callbackUrl(String provider) {
        return properties.callbackUrl(provider);
    }

    // ── 판단 ─────────────────────────────────────────────────

    public sealed interface Outcome permits LoggedIn, NeedsSignup, EmailTaken, Unavailable {
    }

    /** 이미 가입한 회원. 바로 로그인한다. */
    public record LoggedIn(Member member) implements Outcome {
    }

    /** 처음 온 사람. 동의 화면으로 보낸다. */
    public record NeedsSignup(SocialProfile profile) implements Outcome {
    }

    /** 같은 이메일의 다른 계정이 있다. existingProvider 방식으로 로그인하도록 안내한다. */
    public record EmailTaken(String existingProvider) implements Outcome {
    }

    /** 이용이 정지된 계정. */
    public record Unavailable() implements Outcome {
    }

    @Transactional
    public Outcome resolve(SocialProfile profile) {
        Optional<Member> existing = memberRepo.findByProviderAndProviderId(
                providerCode(profile), profile.providerId());
        if (existing.isPresent()) {
            Member member = existing.get();
            if (member.isWithdrawn() || member.isSuspended()) {
                return new Unavailable();
            }
            member.recordLoginSuccess();
            return new LoggedIn(member);
        }

        String email = profile.trustedEmail();
        if (email != null) {
            Optional<Member> sameEmail = memberRepo.findByEmail(email).stream()
                    .filter(m -> !m.isWithdrawn())
                    .findFirst();
            if (sameEmail.isPresent()) {
                return new EmailTaken(sameEmail.get().getProvider());
            }
        }
        return new NeedsSignup(profile);
    }

    /**
     * 동의 화면에서 가입을 마친다.
     *
     * 동의 화면에 머무는 사이 다른 창에서 이미 가입했거나, 같은 이메일 계정이 생겼을 수 있다.
     * 그래서 가입 직전에 한 번 더 판단한다.
     */
    @Transactional
    public Member completeSignup(SocialProfile profile, boolean ageOver14, boolean agreeMarketing) {
        if (!ageOver14) {
            throw new MemberAuthException("만 14세 이상만 가입할 수 있습니다.");
        }

        Outcome outcome = resolve(profile);
        if (outcome instanceof LoggedIn loggedIn) {
            return loggedIn.member();
        }
        if (outcome instanceof EmailTaken) {
            throw new MemberAuthException("이미 같은 이메일로 가입된 계정이 있습니다. 기존 방식으로 로그인해 주세요.");
        }
        if (outcome instanceof Unavailable) {
            throw new MemberAuthException("가입할 수 없는 계정입니다. 고객센터로 문의해 주세요.");
        }

        String email = profile.trustedEmail() != null
                ? profile.trustedEmail()
                : Member.placeholderEmail(profile.provider(), profile.providerId());

        Member member = Member.socialMember(
                providerCode(profile), profile.providerId(), email, displayName(profile.name()));
        member.recordConsents(agreeMarketing, true);
        member.recordLoginSuccess();
        return memberRepo.save(member);
    }

    /** DB 에는 대문자로 저장한다 (member.provider 제약: LOCAL | KAKAO | NAVER). */
    private static String providerCode(SocialProfile profile) {
        return profile.provider().toUpperCase();
    }

    private static String displayName(String name) {
        if (name == null || name.isBlank()) {
            return "회원";
        }
        String trimmed = name.trim();
        return trimmed.length() > NAME_MAX ? trimmed.substring(0, NAME_MAX) : trimmed;
    }
}
