package com.nowonbun.ams;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.Optional;
import org.junit.jupiter.api.Test;

class PermissionServiceTest {
  Menus menus = mock(Menus.class);
  UserGrants users = mock(UserGrants.class);
  RoleGrants roles = mock(RoleGrants.class);
  AdminScopes scopes = mock(AdminScopes.class);
  PermissionService service = new PermissionService(menus, users, roles, scopes);

  UserAccount user() {
    UserAccount u = new UserAccount();
    u.id = 1L;
    u.role = Model.Role.USER;
    u.status = Model.Status.ACTIVE;
    return u;
  }

  Menu menu() {
    Menu m = new Menu();
    m.id = 7L;
    m.name = "공지";
    m.path = "/notice";
    m.active = true;
    return m;
  }

  @Test
  void defaultDeny() {
    when(menus.findById(7L)).thenReturn(Optional.of(menu()));
    assertFalse(service.allowed(user(), 7L, Model.Action.VIEW));
  }

  @Test
  void explicitDenyBeatsRoleGrant() {
    when(menus.findById(7L)).thenReturn(Optional.of(menu()));
    UserGrant g = new UserGrant();
    g.effect = Model.Effect.DENY;
    when(users.findByUserIdAndMenuIdAndAction(1L, 7L, Model.Action.VIEW))
        .thenReturn(Optional.of(g));
    when(roles.existsByRoleAndMenuIdAndAction(Model.Role.USER, 7L, Model.Action.VIEW))
        .thenReturn(true);
    assertFalse(service.allowed(user(), 7L, Model.Action.VIEW));
  }

  @Test
  void explicitAllowBeatsMissingRoleGrant() {
    when(menus.findById(7L)).thenReturn(Optional.of(menu()));
    UserGrant g = new UserGrant();
    g.effect = Model.Effect.ALLOW;
    when(users.findByUserIdAndMenuIdAndAction(1L, 7L, Model.Action.VIEW))
        .thenReturn(Optional.of(g));
    assertTrue(service.allowed(user(), 7L, Model.Action.VIEW));
  }

  @Test
  void inactiveAccountDeniedEvenWithOverride() {
    when(menus.findById(7L)).thenReturn(Optional.of(menu()));
    UserAccount u = user();
    u.status = Model.Status.INACTIVE;
    assertFalse(service.allowed(u, 7L, Model.Action.VIEW));
  }

  @Test
  void adminCannotDelegateWithoutScope() {
    when(menus.findById(7L)).thenReturn(Optional.of(menu()));
    UserAccount u = user();
    u.role = Model.Role.ADMIN;
    assertThrows(
        org.springframework.web.server.ResponseStatusException.class,
        () -> service.canDelegate(u, 7L, Model.Action.CREATE));
  }
}
