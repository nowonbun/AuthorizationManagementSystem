package com.nowonbun.ams;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "menus")
public class Menu {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  public Long parentId;

  @Column(nullable = false, length = 100)
  public String name;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  public Model.MenuType type;

  @Column(nullable = false, unique = true, length = 200)
  public String path;

  @Column(length = 60)
  public String icon;

  public int sortOrder;
  public boolean active = true;
  public boolean visible = true;

  @Column(length = 500)
  public String description;

  public Instant deletedAt;
}
