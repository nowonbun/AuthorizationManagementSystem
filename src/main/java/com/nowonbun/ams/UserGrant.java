package com.nowonbun.ams;

import jakarta.persistence.*;

@Entity
@Table(
    name = "user_menu_permissions",
    uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "menu_id", "action"}))
public class UserGrant {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @Column(name = "user_id", nullable = false)
  public Long userId;

  @Column(name = "menu_id", nullable = false)
  public Long menuId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  public Model.Action action;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 10)
  public Model.Effect effect;
}
