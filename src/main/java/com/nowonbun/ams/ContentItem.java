package com.nowonbun.ams;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "content_items")
public class ContentItem {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @Column(nullable = false)
  public Long menuId;

  @Column(nullable = false, length = 200)
  public String title;

  @Lob
  @Column(nullable = false, columnDefinition = "LONGTEXT")
  public String body;

  public boolean published;

  @Column(nullable = false)
  public Long authorId;

  public Instant createdAt;
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
