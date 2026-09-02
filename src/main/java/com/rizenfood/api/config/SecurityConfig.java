package com.rizenfood.api.config;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import com.rizenfood.api.security.JwtAuthenticationFilter;
import com.rizenfood.api.security.RestAuthEntryPoint;

/**
 * 보안 설정.
 *
 * 경로 규칙은 큰 틀만 잡고, 세부 권한은 각 API 의 @PreAuthorize 가 맡는다.
 * 두 겹으로 두는 이유는 경로 규칙 하나를 잘못 고쳐도 메서드 단의 검사가 남기 때문이다.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity // @PreAuthorize 를 켠다
public class SecurityConfig {

    /** 허용할 프론트 오리진. 와일드카드를 쓰지 않고 화이트리스트로 관리한다. */
    @Value("${app.cors.allowed-origins}")
    private List<String> allowedOrigins;

    private final JwtAuthenticationFilter jwtFilter;
    private final RestAuthEntryPoint authEntryPoint;

    public SecurityConfig(JwtAuthenticationFilter jwtFilter, RestAuthEntryPoint authEntryPoint) {
        this.jwtFilter = jwtFilter;
        this.authEntryPoint = authEntryPoint;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .cors(Customizer.withDefaults())
                // 무상태 토큰 인증이라 서버에 CSRF 토큰을 둘 세션이 없다.
                // 대신 인증 쿠키에 SameSite 를 걸어 교차 사이트 자동 전송을 막는다 (AuthCookies).
                .csrf(csrf -> csrf.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/healthz").permitAll()
                        // 로그인·로그아웃은 토큰이 없는 상태에서 호출된다
                        .requestMatchers("/api/admin/auth/login", "/api/admin/auth/logout").permitAll()
                        // 회원 가입·로그인·재발급·중복확인은 토큰 없이 호출된다
                        .requestMatchers("/api/auth/signup", "/api/auth/login",
                                "/api/auth/refresh", "/api/auth/check-email").permitAll()
                        // 문의 접수는 비회원도 할 수 있다. 로그인 상태면 필터가 인증 정보를 심어주지만
                        // 이 경로 자체는 토큰이 없어도 통과해야 한다.
                        .requestMatchers("/api/inquiries").permitAll()
                        // 장바구니도 비회원이 쓴다. 회원이면 필터가 심어준 인증 정보를
                        // 컨트롤러가 읽어 회원 장바구니로 잇는다.
                        .requestMatchers("/api/cart/**").permitAll()
                        // 관리 API 는 전부 인증이 필요하다. 역할 검사는 @PreAuthorize 가 한다.
                        .requestMatchers("/api/admin/**").authenticated()
                        // 회원 전용 API. 세부 검사는 @PreAuthorize("hasRole('MEMBER')") 가 한다.
                        .requestMatchers("/api/member/**").authenticated()
                        // 공개 조회는 열어둔다. 쓰기는 위 규칙에 걸린다.
                        .requestMatchers(HttpMethod.GET, "/api/products/**", "/api/notices/**",
                                "/api/banners/**", "/api/reviews/**", "/api/settings/**")
                        .permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(e -> e.authenticationEntryPoint(authEntryPoint))
                // API 서버는 로그인 폼으로 리다이렉트하지 않고 401 을 준다.
                .formLogin(form -> form.disable())
                .httpBasic(basic -> basic.disable())
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(allowedOrigins);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Content-Type", "Authorization", "X-Requested-With"));
        // JWT 를 쿠키로 주고받으므로 자격증명 전송을 허용해야 한다.
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    /** 비밀번호 해시. 평문·MD5·SHA 금지 (기획서 §6.2). */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
