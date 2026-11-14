package com.nowonbun.ams;

import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AccountService {
  private final Users users;
  private final Audits audits;
  private final PasswordEncoder encoder;

  AccountService(Users users, Audits audits, PasswordEncoder encoder) {
    this.users = users;
    this.audits = audits;
    this.encoder = encoder;
  }

  public UserAccount current(Authentication auth) {
    if (auth == null || !auth.isAuthenticated())
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
    return users
        .findByLoginId(auth.getName())
        .filter(u -> u.status == Model.Status.ACTIVE)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
  }

  public UserAccount user(Long id) {
    return users
        .findById(id)
        .filter(u -> u.status != Model.Status.DELETED)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "사용자 없음"));
  }

  public void system(UserAccount actor) {
    if (actor.role != Model.Role.SYSTEM_ADMIN)
      throw new ResponseStatusException(HttpStatus.FORBIDDEN);
  }

  public void manager(UserAccount actor) {
    if (actor.role == Model.Role.USER) throw new ResponseStatusException(HttpStatus.FORBIDDEN);
  }

  public void manageable(UserAccount actor, UserAccount target) {
    manager(actor);
    if (actor.role == Model.Role.SYSTEM_ADMIN) return;
    if (target.role != Model.Role.USER || !actor.id.equals(target.managedById))
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "관리 대상 외 사용자");
  }

  public void log(
      UserAccount actor,
      UserAccount target,
      String action,
      String type,
      Long resource,
      String before,
      String after,
      HttpServletRequest request) {
    AuditLog a = new AuditLog();
    a.actorUserId = actor == null ? null : actor.id;
    a.targetUserId = target == null ? null : target.id;
    a.action = action;
    a.resourceType = type;
    a.resourceId = resource;
    a.beforeValue = before;
    a.afterValue = after;
    a.ipAddress = request.getRemoteAddr();
    a.userAgent = request.getHeader("User-Agent");
    audits.save(a);
  }

  @Transactional
  public void loginSucceeded(String login, HttpServletRequest request) {
    users
        .findByLoginId(login)
        .ifPresent(
            u -> {
              u.failedAttempts = 0;
              u.lastLoginAt = Instant.now();
              log(u, u, "LOGIN_SUCCESS", "USER", u.id, null, null, request);
            });
  }

  @Transactional
  public void loginFailed(String login, HttpServletRequest request) {
    if (login == null) return;
    users
        .findByLoginId(login)
        .ifPresent(
            u -> {
              if (u.status == Model.Status.ACTIVE && ++u.failedAttempts >= 5)
                u.status = Model.Status.LOCKED;
              log(
                  u,
                  u,
                  "LOGIN_FAILURE",
                  "USER",
                  u.id,
                  null,
                  "attempts=" + u.failedAttempts,
                  request);
            });
  }

  @Transactional
  public void logout(String login, HttpServletRequest request) {
    users
        .findByLoginId(login)
        .ifPresent(u -> log(u, u, "LOGOUT", "USER", u.id, null, null, request));
  }

  @Transactional
  public void changePassword(
      UserAccount user, String current, String next, HttpServletRequest request) {
    if (!encoder.matches(current, user.passwordHash))
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "현재 비밀번호 불일치");
    if (next == null || next.length() < 12)
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "새 비밀번호는 12자 이상");
    user.passwordHash = encoder.encode(next);
    log(user, user, "PASSWORD_CHANGE", "USER", user.id, null, null, request);
  }
}
