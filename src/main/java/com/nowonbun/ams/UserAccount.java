package com.nowonbun.ams;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(
    name = "user_accounts",
    indexes = {@Index(columnList = "role"), @Index(columnList = "status")})
public class UserAccount {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @Column(nullable = false, unique = true, length = 80)
  public String loginId;

  @Column(nullable = false)
  public String passwordHash;

  @Column(nullable = false, length = 100)
  public String name;

  @Column(nullable = false, unique = true, length = 200)
  public String email;

  @Column(length = 100)
  public String department;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  public Model.Role role;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  public Model.Status status = Model.Status.ACTIVE;

  public Long managedById;
  public int failedAttempts;
  public Instant lastLoginAt;

  @Column(nullable = false, updatable = false)
  public Instant createdAt;

  @Column(nullable = false)
  public Instant updatedAt;

  public Instant deletedAt;

  @PrePersist
  void created() {
    createdAt = updatedAt = Instant.now();
  }

  @PreUpdate
  void updated() {
    updatedAt = Instant.now();
  }
}
