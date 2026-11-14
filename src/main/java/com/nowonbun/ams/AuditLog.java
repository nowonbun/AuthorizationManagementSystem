package com.nowonbun.ams;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(
    name = "audit_logs",
    indexes = {@Index(columnList = "createdAt"), @Index(columnList = "actorUserId")})
public class AuditLog {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  public Long actorUserId;
  public Long targetUserId;

  @Column(nullable = false, length = 80)
  public String action;

  @Column(length = 80)
  public String resourceType;

  public Long resourceId;

  @Lob
  @Column(columnDefinition = "TEXT")
  public String beforeValue;

  @Lob
  @Column(columnDefinition = "TEXT")
  public String afterValue;

  @Column(length = 45)
  public String ipAddress;

  @Column(length = 512)
  public String userAgent;

  @Column(nullable = false)
  public Instant createdAt = Instant.now();
}
