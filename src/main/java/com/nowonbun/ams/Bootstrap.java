package com.nowonbun.ams;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class Bootstrap {
  @Bean
  CommandLineRunner initialize(
      Users users, Menus menus, RoleGrants grants, PasswordEncoder encoder, Environment env) {
    return args -> {
      String login = env.getProperty("BOOTSTRAP_ADMIN_LOGIN"),
          password = env.getProperty("BOOTSTRAP_ADMIN_PASSWORD");
      if (users.count() == 0) {
        if (login == null || login.isBlank() || password == null || password.length() < 12)
          throw new IllegalStateException(
              "첫 실행에는 BOOTSTRAP_ADMIN_LOGIN 및 12자 이상의 BOOTSTRAP_ADMIN_PASSWORD가 필요합니다.");
        UserAccount admin = new UserAccount();
        admin.loginId = login;
        admin.passwordHash = encoder.encode(password);
        admin.name = "시스템 관리자";
        admin.email = "sysadmin@localhost.invalid";
        admin.role = Model.Role.SYSTEM_ADMIN;
        users.save(admin);
      }
      String[][] defaults = {
        {"대시보드", "/dashboard"},
        {"사용자 관리", "/users"},
        {"메뉴 관리", "/menus"},
        {"콘텐츠 관리", "/contents"},
        {"권한 관리", "/permissions"},
        {"감사 로그", "/audits"}
      };
      int order = 0;
      for (String[] value : defaults) {
        if (menus.findByPath(value[1]).isEmpty()) {
          Menu m = new Menu();
          m.name = value[0];
          m.path = value[1];
          m.type = Model.MenuType.MENU;
          m.sortOrder = order;
          menus.save(m);
        }
        order++;
      }
      for (String path : new String[] {"/dashboard"}) {
        Menu m = menus.findByPath(path).orElseThrow();
        if (!grants.existsByRoleAndMenuIdAndAction(Model.Role.USER, m.id, Model.Action.VIEW)) {
          RoleGrant g = new RoleGrant();
          g.role = Model.Role.USER;
          g.menuId = m.id;
          g.action = Model.Action.VIEW;
          grants.save(g);
        }
      }
    };
  }
}
