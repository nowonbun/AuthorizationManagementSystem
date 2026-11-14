package com.nowonbun.ams;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@Configuration
public class SecurityConfig {
  @Bean
  PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder(12);
  }

  @Bean
  UserDetailsService userDetailsService(Users users) {
    return login -> {
      UserAccount user =
          users
              .findByLoginId(login)
              .orElseThrow(() -> new UsernameNotFoundException("Invalid credentials"));
      return User.withUsername(user.loginId)
          .password(user.passwordHash)
          .roles(user.role.name())
          .disabled(user.status != Model.Status.ACTIVE && user.status != Model.Status.LOCKED)
          .accountLocked(user.status == Model.Status.LOCKED)
          .build();
    };
  }

  @Bean
  SecurityFilterChain filterChain(HttpSecurity http, AccountService accounts) throws Exception {
    http.authorizeHttpRequests(
            a ->
                a.requestMatchers(
                        "/",
                        "/index.html",
                        "/app.css",
                        "/app.js",
                        "/api/csrf",
                        "/actuator/health",
                        "/error")
                    .permitAll()
                    .anyRequest()
                    .authenticated())
        .csrf(c -> c.csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse()))
        .formLogin(
            f ->
                f.loginProcessingUrl("/api/login")
                    .successHandler(
                        (req, res, auth) -> {
                          accounts.loginSucceeded(auth.getName(), req);
                          res.setStatus(204);
                        })
                    .failureHandler(
                        (req, res, ex) -> {
                          accounts.loginFailed(req.getParameter("username"), req);
                          res.sendError(401, "로그인 실패");
                        }))
        .logout(
            l ->
                l.logoutUrl("/api/logout")
                    .logoutSuccessHandler(
                        (req, res, auth) -> {
                          if (auth != null) accounts.logout(auth.getName(), req);
                          res.setStatus(204);
                        }))
        .exceptionHandling(
            e ->
                e.authenticationEntryPoint(
                    (req, res, ex) -> res.sendError(HttpStatus.UNAUTHORIZED.value())))
        .sessionManagement(s -> s.sessionFixation(f -> f.migrateSession()).maximumSessions(1));
    return http.build();
  }

  @RestController
  static class CsrfController {
    @GetMapping("/api/csrf")
    Object csrf(CsrfToken token) {
      return java.util.Map.of("headerName", token.getHeaderName(), "token", token.getToken());
    }
  }
}
