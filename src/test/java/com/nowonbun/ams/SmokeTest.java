package com.nowonbun.ams;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(
    properties = {
      "spring.datasource.url=jdbc:h2:mem:ams;MODE=MySQL;DB_CLOSE_DELAY=-1",
      "spring.datasource.driver-class-name=org.h2.Driver",
      "spring.datasource.username=sa",
      "spring.datasource.password=",
      "spring.jpa.hibernate.ddl-auto=create-drop",
      "BOOTSTRAP_ADMIN_LOGIN=testadmin",
      "BOOTSTRAP_ADMIN_PASSWORD=test-admin-password-long"
    })
@AutoConfigureMockMvc
class SmokeTest {
  @Autowired MockMvc mvc;

  @Test
  void csrfEndpointAvailableBeforeLogin() throws Exception {
    mvc.perform(get("/api/csrf"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.token").isNotEmpty());
  }

  @Test
  void privateApiNeedsLogin() throws Exception {
    mvc.perform(get("/api/users")).andExpect(status().isUnauthorized());
  }

  @Test
  void bootstrapAdminCanLogin() throws Exception {
    mvc.perform(
            post("/api/login")
                .with(csrf())
                .param("username", "testadmin")
                .param("password", "test-admin-password-long"))
        .andExpect(status().isNoContent());
  }
}
