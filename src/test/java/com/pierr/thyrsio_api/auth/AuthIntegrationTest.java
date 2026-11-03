package com.pierr.thyrsio_api.auth;

import com.pierr.thyrsio_api.TestcontainersConfig;
import com.pierr.thyrsio_api.user.Role;
import com.pierr.thyrsio_api.user.User;
import com.pierr.thyrsio_api.user.UserRepository;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest         // démarre toute l'appli
@AutoConfigureMockMvc   // fournit MockMvc pour simuler des requêtes HTTP
@Import(TestcontainersConfig.class)
public class AuthIntegrationTest {

    private static final String PASSWORD = "motdepasse123";

    @Autowired
    MockMvc mockMvc;
    @Autowired
    private UserRepository userRepository;

    @Test
    void register_creates_user_without_exposing_password() throws Exception {
        String email = uniqueEmail();

        mockMvc.perform(post("/api/auth/register")
                .with(xsrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(registerJson("Pierre", email, PASSWORD)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").exists())
            .andExpect(jsonPath("$.email").value(email))
            .andExpect(jsonPath("$.password").doesNotExist())
            .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    @Test
    void register_with_short_password_returns_400_with_field_error() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                    .with(xsrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(registerJson("Pierre", uniqueEmail(), "court")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.password").exists());
    }

    @Test
    void request_without_csrf_token_is_rejected() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(registerJson("Pierre", uniqueEmail(), PASSWORD)))
                .andExpect(status().isForbidden());
    }

    @Test
    void me_without_login_returns_401() throws Exception {
        mockMvc.perform(get("/api/users/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void login_then_me_returns_profile() throws Exception {
        String email = uniqueEmail();
        register(email);
        MockHttpSession session = login(email, PASSWORD);

        mockMvc.perform(get("/api/users/me").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(email));
    }

    @Test
    void register_with_already_taken_email_returns_409() throws Exception {
        String email = uniqueEmail();
        register(email);

        mockMvc.perform(post("/api/auth/register")
                    .with(xsrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(registerJson("Pierre", email, PASSWORD)))
                .andExpect(status().isConflict());
    }

    @Test
    void login_with_wrong_password_returns_401() throws Exception {
        String email = uniqueEmail();
        register(email);

        mockMvc.perform(post("/api/auth/login")
                        .with(xsrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "%s", "password": "%s"}
                                """.formatted(email, "wrongpassword")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void get_users_with_user_role_returns_403() throws Exception {
        String email = uniqueEmail();
        register(email);
        MockHttpSession session = login(email, PASSWORD);

        mockMvc.perform(get("/api/users").session(session))
                .andExpect(status().isForbidden());
    }

    @Test
    void patch_with_new_email_then_get_me_returns_200() throws Exception {
        String email = uniqueEmail();
        register(email);
        MockHttpSession session = login(email, PASSWORD);

        String newEmail = uniqueEmail();

        mockMvc.perform(patch("/api/users/me").session(session)
                        .with(xsrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                        {"email": "%s"}
                        """.formatted(newEmail)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/users/me").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(newEmail));
    }

    @Test
    void delete_me_then_get_me_returns_401() throws Exception
    {
        String email = uniqueEmail();
        register(email);
        MockHttpSession session = login(email, PASSWORD);

        mockMvc.perform(delete("/api/users/me").session(session)
                        .with(xsrf()))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/users/me").session(session))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void get_users_with_admin_returns_200() throws Exception {
        String email = uniqueEmail();
        register(email);

        User user = userRepository.findByEmail(email).orElseThrow();
        user.setRole(Role.ADMIN);
        userRepository.save(user);

        MockHttpSession session = login(email, PASSWORD);

        mockMvc.perform(get("/api/users").session(session))
                .andExpect(status().isOk());
    }

    // --- Méthodes utilitaires ---

    private static final String CSRF_TOKEN = "test-csrf-token";

    /** Simule Angular : même valeur dans le cookie XSRF-TOKEN et dans l'en-tête X-XSRF-TOKEN. */
    private static RequestPostProcessor xsrf() {
        return request -> {
            request.setCookies(new Cookie("XSRF-TOKEN", CSRF_TOKEN));
            request.addHeader("X-XSRF-TOKEN", CSRF_TOKEN);
            return request;
        };
    }

    private void register(String email) throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .with(xsrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerJson("Pierre", email, PASSWORD)))
                .andExpect(status().isCreated());
    }

    /** Se connecte et renvoie la session, à réutiliser dans les requêtes suivantes. */
    private MockHttpSession login(String email, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .with(xsrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "%s", "password": "%s"}
                                """.formatted(email, password)))
                .andExpect(status().isOk())
                .andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }

    private static String registerJson(String name, String email, String password) {
        return """
                {"name": "%s", "email": "%s", "password": "%s"}
                """.formatted(name, email, password);
    }

    /** Un email différent à chaque test : les tests ne se gênent pas entre eux. */
    private static String uniqueEmail() {
        return "user-" + UUID.randomUUID() + "@example.com";
    }
}
