package com.nowonbun.ams;

import java.util.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

interface Users extends JpaRepository<UserAccount, Long> {
  Optional<UserAccount> findByLoginId(String loginId);

  boolean existsByLoginId(String loginId);

  boolean existsByEmail(String email);

  List<UserAccount> findByStatusNotOrderByIdAsc(Model.Status status);

  long countByStatusNot(Model.Status status);

  long countByRoleAndStatusNot(Model.Role role, Model.Status status);
}

interface Menus extends JpaRepository<Menu, Long> {
  Optional<Menu> findByPath(String path);

  List<Menu> findByDeletedAtIsNullOrderBySortOrderAscIdAsc();

  boolean existsByParentIdAndDeletedAtIsNull(Long parentId);
}

interface RoleGrants extends JpaRepository<RoleGrant, Long> {
  boolean existsByRoleAndMenuIdAndAction(Model.Role role, Long menuId, Model.Action action);

  List<RoleGrant> findByRole(Model.Role role);

  List<RoleGrant> findByMenuId(Long menuId);

  void deleteByRoleAndMenuIdAndAction(Model.Role role, Long menuId, Model.Action action);
}

interface UserGrants extends JpaRepository<UserGrant, Long> {
  Optional<UserGrant> findByUserIdAndMenuIdAndAction(Long userId, Long menuId, Model.Action action);

  List<UserGrant> findByUserId(Long userId);

  List<UserGrant> findByMenuId(Long menuId);

  void deleteByUserIdAndMenuIdAndAction(Long userId, Long menuId, Model.Action action);
}

interface AdminScopes extends JpaRepository<AdminScope, Long> {
  boolean existsByAdminUserIdAndMenuIdAndAction(Long adminUserId, Long menuId, Model.Action action);

  List<AdminScope> findByAdminUserId(Long adminUserId);

  List<AdminScope> findByMenuId(Long menuId);

  void deleteByAdminUserIdAndMenuIdAndAction(Long adminUserId, Long menuId, Model.Action action);
}

interface Contents extends JpaRepository<ContentItem, Long> {
  List<ContentItem> findByMenuIdAndDeletedAtIsNullOrderByIdDesc(Long menuId);

  List<ContentItem> findByDeletedAtIsNullOrderByIdDesc();

  long countByDeletedAtIsNull();

  boolean existsByMenuIdAndDeletedAtIsNull(Long menuId);
}

interface Audits extends JpaRepository<AuditLog, Long> {
  Page<AuditLog> findAllByOrderByCreatedAtDesc(Pageable pageable);

  Page<AuditLog> findByTargetUserIdOrActorUserIdOrderByCreatedAtDesc(
      Long targetId, Long actorId, Pageable pageable);
}
