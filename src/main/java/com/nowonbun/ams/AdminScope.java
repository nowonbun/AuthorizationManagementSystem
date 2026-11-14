package com.nowonbun.ams;

import jakarta.persistence.*;

@Entity
@Table(
    name = "admin_scopes",
    uniqueConstraints = @UniqueConstraint(columnNames = {"admin_user_id", "menu_id", "action"}))
public class AdminScope {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @Column(name = "admin_user_id", nullable = false)
  public Long adminUserId;

  @Column(name = "menu_id", nullable = false)
  public Long menuId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  public Model.Action action;
}
