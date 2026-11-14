package com.nowonbun.ams;

import jakarta.persistence.*;

@Entity
@Table(
    name = "role_menu_permissions",
    uniqueConstraints = @UniqueConstraint(columnNames = {"role", "menu_id", "action"}))
public class RoleGrant {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  public Model.Role role;

  @Column(name = "menu_id", nullable = false)
  public Long menuId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  public Model.Action action;
}
