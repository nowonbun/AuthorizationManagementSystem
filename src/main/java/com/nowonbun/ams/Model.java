package com.nowonbun.ams;

public final class Model {
  private Model() {}

  public enum Role {
    SYSTEM_ADMIN,
    ADMIN,
    USER
  }

  public enum Status {
    ACTIVE,
    INACTIVE,
    LOCKED,
    SUSPENDED,
    DELETED
  }

  public enum Action {
    VIEW,
    CREATE,
    UPDATE,
    DELETE,
    MANAGE
  }

  public enum Effect {
    ALLOW,
    DENY
  }

  public enum MenuType {
    MENU,
    CONTENT,
    LINK
  }
}
