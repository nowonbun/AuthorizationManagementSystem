package com.nowonbun.ams;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class PermissionService {
  private final Menus menus;
  private final UserGrants userGrants;
  private final RoleGrants roleGrants;
  private final AdminScopes scopes;

  PermissionService(Menus menus, UserGrants userGrants, RoleGrants roleGrants, AdminScopes scopes) {
    this.menus = menus;
    this.userGrants = userGrants;
    this.roleGrants = roleGrants;
    this.scopes = scopes;
  }

  public Menu menu(Long id) {
    return menus
        .findById(id)
        .filter(m -> m.deletedAt == null)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "메뉴 없음"));
  }

  public boolean allowed(UserAccount user, Long menuId, Model.Action action) {
    Menu menu = menu(menuId);
    if (!menu.active || user.status != Model.Status.ACTIVE) return false;
    if (user.role == Model.Role.SYSTEM_ADMIN) return true;
    var override = userGrants.findByUserIdAndMenuIdAndAction(user.id, menuId, action);
    if (override.isPresent()) return override.get().effect == Model.Effect.ALLOW;
    return roleGrants.existsByRoleAndMenuIdAndAction(user.role, menuId, action);
  }

  public void require(UserAccount user, Long menuId, Model.Action action) {
    if (!allowed(user, menuId, action))
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "권한 없음");
  }

  public void canDelegate(UserAccount actor, Long menuId, Model.Action action) {
    if (actor.role == Model.Role.SYSTEM_ADMIN) return;
    if (actor.role != Model.Role.ADMIN
        || !scopes.existsByAdminUserIdAndMenuIdAndAction(actor.id, menuId, action)
        || !allowed(actor, menuId, action))
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "위임 범위 초과");
  }
}
