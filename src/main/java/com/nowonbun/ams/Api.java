package com.nowonbun.ams;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.*;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api")
public class Api {
  private final AccountService accounts;
  private final PermissionService permissions;
  private final Users users;
  private final Menus menus;
  private final UserGrants userGrants;
  private final RoleGrants roleGrants;
  private final AdminScopes scopes;
  private final Contents contents;
  private final Audits audits;
  private final PasswordEncoder encoder;

  Api(
      AccountService accounts,
      PermissionService permissions,
      Users users,
      Menus menus,
      UserGrants userGrants,
      RoleGrants roleGrants,
      AdminScopes scopes,
      Contents contents,
      Audits audits,
      PasswordEncoder encoder) {
    this.accounts = accounts;
    this.permissions = permissions;
    this.users = users;
    this.menus = menus;
    this.userGrants = userGrants;
    this.roleGrants = roleGrants;
    this.scopes = scopes;
    this.contents = contents;
    this.audits = audits;
    this.encoder = encoder;
  }

  record UserView(
      Long id,
      String loginId,
      String name,
      String email,
      String department,
      Model.Role role,
      Model.Status status,
      Long managedById,
      Instant lastLoginAt,
      Instant createdAt) {}

  UserView view(UserAccount u) {
    return new UserView(
        u.id,
        u.loginId,
        u.name,
        u.email,
        u.department,
        u.role,
        u.status,
        u.managedById,
        u.lastLoginAt,
        u.createdAt);
  }

  UserAccount me(Authentication a) {
    return accounts.current(a);
  }

  void userManagement(UserAccount actor, Model.Action action) {
    if (actor.role == Model.Role.SYSTEM_ADMIN) return;
    Menu m = menus.findByPath("/users").orElseThrow();
    permissions.canDelegate(actor, m.id, action);
  }

  @GetMapping("/me")
  Object meEndpoint(Authentication a) {
    return view(me(a));
  }

  @PostMapping("/me/password")
  @Transactional
  void password(Authentication a, @RequestBody PasswordChange body, HttpServletRequest request) {
    accounts.changePassword(me(a), body.currentPassword, body.newPassword, request);
  }

  record PasswordChange(String currentPassword, String newPassword) {}

  @GetMapping("/dashboard")
  Object dashboard(Authentication a) {
    UserAccount actor = me(a);
    if (actor.role == Model.Role.USER) return Map.of("myMenuCount", myMenus(a).size());
    return Map.of(
        "users",
        users.countByStatusNot(Model.Status.DELETED),
        "admins",
        users.countByRoleAndStatusNot(Model.Role.ADMIN, Model.Status.DELETED),
        "menus",
        menus.findByDeletedAtIsNullOrderBySortOrderAscIdAsc().size(),
        "contents",
        contents.countByDeletedAtIsNull());
  }

  @GetMapping("/users")
  Object users(Authentication a) {
    UserAccount actor = me(a);
    userManagement(actor, Model.Action.VIEW);
    return users.findByStatusNotOrderByIdAsc(Model.Status.DELETED).stream()
        .filter(u -> actor.role == Model.Role.SYSTEM_ADMIN || actor.id.equals(u.managedById))
        .map(this::view)
        .toList();
  }

  @GetMapping("/users/{id}")
  Object user(Authentication a, @PathVariable Long id) {
    UserAccount actor = me(a), target = accounts.user(id);
    accounts.manageable(actor, target);
    return view(target);
  }

  record UserInput(
      @NotBlank String loginId,
      @NotBlank String name,
      @Email @NotBlank String email,
      String department,
      @NotNull Model.Role role,
      @NotBlank String password) {}

  @PostMapping("/users")
  @Transactional
  Object createUser(
      Authentication a, @Valid @RequestBody UserInput body, HttpServletRequest request) {
    UserAccount actor = me(a);
    userManagement(actor, Model.Action.CREATE);
    if (actor.role != Model.Role.SYSTEM_ADMIN && body.role != Model.Role.USER)
      throw new ResponseStatusException(HttpStatus.FORBIDDEN);
    if (body.password.length() < 12)
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "비밀번호 12자 이상");
    if (users.existsByLoginId(body.loginId) || users.existsByEmail(body.email))
      throw new ResponseStatusException(HttpStatus.CONFLICT, "아이디 또는 이메일 중복");
    UserAccount u = new UserAccount();
    u.loginId = body.loginId;
    u.name = body.name;
    u.email = body.email;
    u.department = body.department;
    u.role = body.role;
    u.passwordHash = encoder.encode(body.password);
    u.managedById = actor.role == Model.Role.ADMIN ? actor.id : null;
    users.save(u);
    accounts.log(actor, u, "USER_CREATE", "USER", u.id, null, u.role.name(), request);
    return view(u);
  }

  record UserUpdate(
      @NotBlank String name,
      @Email @NotBlank String email,
      String department,
      @NotNull Model.Status status,
      @NotNull Model.Role role) {}

  @PutMapping("/users/{id}")
  @Transactional
  Object updateUser(
      Authentication a,
      @PathVariable Long id,
      @Valid @RequestBody UserUpdate body,
      HttpServletRequest request) {
    UserAccount actor = me(a), u = accounts.user(id);
    accounts.manageable(actor, u);
    userManagement(actor, Model.Action.UPDATE);
    if (actor.role != Model.Role.SYSTEM_ADMIN && body.role != Model.Role.USER)
      throw new ResponseStatusException(HttpStatus.FORBIDDEN);
    if (u.id.equals(actor.id) && (body.status != Model.Status.ACTIVE || body.role != actor.role))
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "자기 계정의 역할/상태 변경 불가");
    if (body.status == Model.Status.DELETED)
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "삭제 API 사용");
    if (users.existsByEmail(body.email) && !u.email.equals(body.email))
      throw new ResponseStatusException(HttpStatus.CONFLICT, "이메일 중복");
    String before = u.name + "/" + u.email + "/" + u.role + "/" + u.status;
    u.name = body.name;
    u.email = body.email;
    u.department = body.department;
    u.role = body.role;
    u.status = body.status;
    if (body.status == Model.Status.ACTIVE) u.failedAttempts = 0;
    accounts.log(
        actor,
        u,
        "USER_UPDATE",
        "USER",
        u.id,
        before,
        u.name + "/" + u.email + "/" + u.role + "/" + u.status,
        request);
    return view(u);
  }

  @DeleteMapping("/users/{id}")
  @Transactional
  void deleteUser(Authentication a, @PathVariable Long id, HttpServletRequest request) {
    UserAccount actor = me(a), u = accounts.user(id);
    accounts.manageable(actor, u);
    userManagement(actor, Model.Action.DELETE);
    if (actor.id.equals(u.id))
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "자기 계정 삭제 불가");
    u.status = Model.Status.DELETED;
    u.deletedAt = Instant.now();
    accounts.log(actor, u, "USER_DELETE", "USER", u.id, null, "DELETED", request);
  }

  @GetMapping("/menus")
  Object menus(Authentication a) {
    UserAccount actor = me(a);
    accounts.system(actor);
    return menus.findByDeletedAtIsNullOrderBySortOrderAscIdAsc();
  }

  @GetMapping("/my-menus")
  List<Menu> myMenus(Authentication a) {
    UserAccount actor = me(a);
    return menus.findByDeletedAtIsNullOrderBySortOrderAscIdAsc().stream()
        .filter(m -> m.visible && m.active && permissions.allowed(actor, m.id, Model.Action.VIEW))
        .toList();
  }

  @GetMapping("/permissions/menus")
  Object permissionMenus(Authentication a) {
    UserAccount actor = me(a);
    accounts.manager(actor);
    if (actor.role == Model.Role.SYSTEM_ADMIN)
      return menus.findByDeletedAtIsNullOrderBySortOrderAscIdAsc();
    Set<Long> scoped = new HashSet<>();
    for (AdminScope s : scopes.findByAdminUserId(actor.id))
      if (permissions.allowed(actor, s.menuId, s.action)) scoped.add(s.menuId);
    return menus.findByDeletedAtIsNullOrderBySortOrderAscIdAsc().stream()
        .filter(m -> scoped.contains(m.id))
        .toList();
  }

  record MenuInput(
      @NotBlank String name,
      @NotBlank String path,
      @NotNull Model.MenuType type,
      Long parentId,
      String icon,
      int sortOrder,
      boolean active,
      boolean visible,
      String description) {}

  void validateMenu(MenuInput b, Long editingId) {
    if (!b.path.startsWith("/")
        || b.path.contains("..")
        || b.path.startsWith("/api/")
        || b.path.equals("/api"))
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "잘못된 경로");
    if (b.parentId != null) {
      Menu p = permissions.menu(b.parentId);
      Set<Long> seen = new HashSet<>();
      while (p != null) {
        if (p.id.equals(editingId) || !seen.add(p.id))
          throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "메뉴 순환 참조");
        p = p.parentId == null ? null : permissions.menu(p.parentId);
      }
    }
    menus
        .findByPath(b.path)
        .ifPresent(
            m -> {
              if (m.deletedAt == null && !m.id.equals(editingId))
                throw new ResponseStatusException(HttpStatus.CONFLICT, "경로 중복");
            });
  }

  @PostMapping("/menus")
  @Transactional
  Object createMenu(Authentication a, @Valid @RequestBody MenuInput b, HttpServletRequest request) {
    UserAccount actor = me(a);
    accounts.system(actor);
    validateMenu(b, null);
    Menu m = new Menu();
    copyMenu(m, b);
    menus.save(m);
    accounts.log(actor, null, "MENU_CREATE", "MENU", m.id, null, m.name + " " + m.path, request);
    return m;
  }

  void copyMenu(Menu m, MenuInput b) {
    m.name = b.name;
    m.path = b.path;
    m.type = b.type;
    m.parentId = b.parentId;
    m.icon = b.icon;
    m.sortOrder = b.sortOrder;
    m.active = b.active;
    m.visible = b.visible;
    m.description = b.description;
  }

  @PutMapping("/menus/{id}")
  @Transactional
  Object updateMenu(
      Authentication a,
      @PathVariable Long id,
      @Valid @RequestBody MenuInput b,
      HttpServletRequest request) {
    UserAccount actor = me(a);
    accounts.system(actor);
    Menu m = permissions.menu(id);
    validateMenu(b, id);
    String before = m.name + " " + m.path;
    copyMenu(m, b);
    accounts.log(actor, null, "MENU_UPDATE", "MENU", id, before, m.name + " " + m.path, request);
    return m;
  }

  @DeleteMapping("/menus/{id}")
  @Transactional
  void deleteMenu(Authentication a, @PathVariable Long id, HttpServletRequest request) {
    UserAccount actor = me(a);
    accounts.system(actor);
    Menu m = permissions.menu(id);
    if (menus.existsByParentIdAndDeletedAtIsNull(id)
        || contents.existsByMenuIdAndDeletedAtIsNull(id))
      throw new ResponseStatusException(HttpStatus.CONFLICT, "하위 메뉴 또는 콘텐츠 존재");
    m.deletedAt = Instant.now();
    m.active = false;
    accounts.log(actor, null, "MENU_DELETE", "MENU", id, m.name, null, request);
  }

  record GrantInput(
      Long userId,
      Model.Role role,
      Long menuId,
      @NotNull Model.Action action,
      Model.Effect effect) {}

  @GetMapping("/permissions/user/{id}")
  Object userPermissions(Authentication a, @PathVariable Long id) {
    UserAccount actor = me(a), target = accounts.user(id);
    accounts.manageable(actor, target);
    return userGrants.findByUserId(id);
  }

  @PutMapping("/permissions/user")
  @Transactional
  void userPermission(Authentication a, @RequestBody GrantInput b, HttpServletRequest request) {
    if (b.userId == null || b.menuId == null || b.action == null)
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
    UserAccount actor = me(a), target = accounts.user(b.userId);
    accounts.manageable(actor, target);
    if (target.role == Model.Role.SYSTEM_ADMIN)
      throw new ResponseStatusException(HttpStatus.FORBIDDEN);
    permissions.menu(b.menuId);
    permissions.canDelegate(actor, b.menuId, b.action);
    var old = userGrants.findByUserIdAndMenuIdAndAction(b.userId, b.menuId, b.action);
    String before = old.map(g -> g.effect.name()).orElse("NONE");
    if (b.effect == null) old.ifPresent(userGrants::delete);
    else {
      UserGrant g = old.orElseGet(UserGrant::new);
      g.userId = b.userId;
      g.menuId = b.menuId;
      g.action = b.action;
      g.effect = b.effect;
      userGrants.save(g);
    }
    accounts.log(
        actor,
        target,
        "USER_PERMISSION_UPDATE",
        "MENU",
        b.menuId,
        b.action + ":" + before,
        b.action + ":" + (b.effect == null ? "NONE" : b.effect),
        request);
  }

  @GetMapping("/permissions/role/{role}")
  Object rolePermissions(Authentication a, @PathVariable Model.Role role) {
    accounts.system(me(a));
    return roleGrants.findByRole(role);
  }

  @PutMapping("/permissions/role")
  @Transactional
  void rolePermission(Authentication a, @RequestBody GrantInput b, HttpServletRequest request) {
    UserAccount actor = me(a);
    accounts.system(actor);
    if (b.role == null || b.menuId == null || b.action == null || b.role == Model.Role.SYSTEM_ADMIN)
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
    permissions.menu(b.menuId);
    boolean before = roleGrants.existsByRoleAndMenuIdAndAction(b.role, b.menuId, b.action);
    if (b.effect == Model.Effect.ALLOW && !before) {
      RoleGrant g = new RoleGrant();
      g.role = b.role;
      g.menuId = b.menuId;
      g.action = b.action;
      roleGrants.save(g);
    } else if (b.effect != Model.Effect.ALLOW && before)
      roleGrants.deleteByRoleAndMenuIdAndAction(b.role, b.menuId, b.action);
    accounts.log(
        actor,
        null,
        "ROLE_PERMISSION_UPDATE",
        "MENU",
        b.menuId,
        b.role + ":" + b.action + ":" + before,
        b.role + ":" + b.action + ":" + (b.effect == Model.Effect.ALLOW),
        request);
  }

  @GetMapping("/permissions/scope/{adminId}")
  Object scopes(Authentication a, @PathVariable Long adminId) {
    accounts.system(me(a));
    UserAccount admin = accounts.user(adminId);
    if (admin.role != Model.Role.ADMIN) throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
    return scopes.findByAdminUserId(adminId);
  }

  record ScopeInput(Long adminId, Long menuId, Model.Action action, boolean enabled) {}

  @PutMapping("/permissions/scope")
  @Transactional
  void scope(Authentication a, @RequestBody ScopeInput b, HttpServletRequest request) {
    UserAccount actor = me(a);
    accounts.system(actor);
    if (b.adminId == null || b.menuId == null || b.action == null)
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
    UserAccount admin = accounts.user(b.adminId);
    if (admin.role != Model.Role.ADMIN) throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
    permissions.menu(b.menuId);
    boolean before = scopes.existsByAdminUserIdAndMenuIdAndAction(b.adminId, b.menuId, b.action);
    if (b.enabled && !before) {
      AdminScope s = new AdminScope();
      s.adminUserId = b.adminId;
      s.menuId = b.menuId;
      s.action = b.action;
      scopes.save(s);
    } else if (!b.enabled && before)
      scopes.deleteByAdminUserIdAndMenuIdAndAction(b.adminId, b.menuId, b.action);
    accounts.log(
        actor,
        admin,
        "ADMIN_SCOPE_UPDATE",
        "MENU",
        b.menuId,
        b.action + ":" + before,
        b.action + ":" + b.enabled,
        request);
  }

  @GetMapping("/contents")
  Object contents(Authentication a, @RequestParam(required = false) Long menuId) {
    UserAccount actor = me(a);
    if (menuId != null) {
      permissions.require(actor, menuId, Model.Action.VIEW);
      return contents.findByMenuIdAndDeletedAtIsNullOrderByIdDesc(menuId).stream()
          .filter(c -> c.published || actor.role != Model.Role.USER)
          .toList();
    }
    return contents.findByDeletedAtIsNullOrderByIdDesc().stream()
        .filter(
            c ->
                permissions.allowed(actor, c.menuId, Model.Action.VIEW)
                    && (c.published || actor.role != Model.Role.USER))
        .toList();
  }

  @GetMapping("/contents/{id}")
  Object content(Authentication a, @PathVariable Long id) {
    ContentItem c = content(id);
    UserAccount actor = me(a);
    permissions.require(actor, c.menuId, Model.Action.VIEW);
    if (!c.published && actor.role == Model.Role.USER)
      throw new ResponseStatusException(HttpStatus.FORBIDDEN);
    return c;
  }

  ContentItem content(Long id) {
    return contents
        .findById(id)
        .filter(c -> c.deletedAt == null)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
  }

  record ContentInput(
      @NotNull Long menuId, @NotBlank String title, @NotBlank String body, boolean published) {}

  void contentMenu(Long id) {
    Menu m = permissions.menu(id);
    if (m.type != Model.MenuType.CONTENT)
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "콘텐츠 메뉴가 아님");
  }

  @PostMapping("/contents")
  @Transactional
  Object createContent(
      Authentication a, @Valid @RequestBody ContentInput b, HttpServletRequest request) {
    UserAccount actor = me(a);
    contentMenu(b.menuId);
    permissions.require(actor, b.menuId, Model.Action.CREATE);
    ContentItem c = new ContentItem();
    c.menuId = b.menuId;
    c.title = b.title;
    c.body = b.body;
    c.published = b.published;
    c.authorId = actor.id;
    contents.save(c);
    accounts.log(actor, null, "CONTENT_CREATE", "CONTENT", c.id, null, c.title, request);
    return c;
  }

  @PutMapping("/contents/{id}")
  @Transactional
  Object updateContent(
      Authentication a,
      @PathVariable Long id,
      @Valid @RequestBody ContentInput b,
      HttpServletRequest request) {
    UserAccount actor = me(a);
    ContentItem c = content(id);
    contentMenu(b.menuId);
    permissions.require(actor, c.menuId, Model.Action.UPDATE);
    permissions.require(actor, b.menuId, Model.Action.UPDATE);
    String before = c.title + "/" + c.published;
    c.menuId = b.menuId;
    c.title = b.title;
    c.body = b.body;
    c.published = b.published;
    accounts.log(
        actor, null, "CONTENT_UPDATE", "CONTENT", id, before, c.title + "/" + c.published, request);
    return c;
  }

  @DeleteMapping("/contents/{id}")
  @Transactional
  void deleteContent(Authentication a, @PathVariable Long id, HttpServletRequest request) {
    UserAccount actor = me(a);
    ContentItem c = content(id);
    permissions.require(actor, c.menuId, Model.Action.DELETE);
    c.deletedAt = Instant.now();
    accounts.log(actor, null, "CONTENT_DELETE", "CONTENT", id, c.title, null, request);
  }

  @GetMapping("/audits")
  Object audits(Authentication a, @RequestParam(defaultValue = "0") int page) {
    UserAccount actor = me(a);
    accounts.system(actor);
    return audits.findAllByOrderByCreatedAtDesc(PageRequest.of(Math.max(0, page), 30));
  }
}
