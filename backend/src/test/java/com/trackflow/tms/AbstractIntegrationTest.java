package com.trackflow.tms;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Base for integration tests: full application, real MySQL with Flyway
 * migrations and demo data. All subclasses share one Spring context and one container.
 *
 * <p>Demo data: users 1 admin, 2 pm, 3 dev, 4 dev2, 5 user; project 1 TMS
 * (owner pm; members pm, dev, dev2, user), project 2 OPS (owner admin;
 * members admin, pm, dev, dev2). Tests must not change the demo users'
 * roles or passwords; create fresh users for that.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({"test", "demo-data"})
@Import(TestcontainersConfiguration.class)
public abstract class AbstractIntegrationTest {

    protected static final String DEMO_PASSWORD = "Password@123";
    protected static final String REFRESH_COOKIE = "tf_refresh";

    protected static final String ADMIN = "admin@trackflow.dev";
    protected static final String PM = "pm@trackflow.dev";
    protected static final String DEV = "dev@trackflow.dev";
    protected static final String DEV2 = "dev2@trackflow.dev";
    protected static final String USER = "user@trackflow.dev";

    protected static final long ADMIN_ID = 1;
    protected static final long PM_ID = 2;
    protected static final long DEV_ID = 3;
    protected static final long DEV2_ID = 4;
    protected static final long USER_ID = 5;
    protected static final long TMS = 1;
    protected static final long OPS = 2;

    /** Access tokens live 15 minutes; one login per demo user is enough for the whole run. */
    private static final Map<String, String> TOKENS = new ConcurrentHashMap<>();

    @Autowired
    protected MockMvc mvc;

    @Autowired
    protected ObjectMapper objectMapper;

    @Autowired
    protected JdbcTemplate jdbc;

    protected String json(Object body) throws Exception {
        return objectMapper.writeValueAsString(body);
    }

    protected MvcResult login(String email, String password) throws Exception {
        return mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", email, "password", password))))
                .andExpect(status().isOk())
                .andReturn();
    }

    protected String accessTokenFor(String email) throws Exception {
        return accessToken(login(email, DEMO_PASSWORD));
    }

    protected String token(String email) {
        return TOKENS.computeIfAbsent(email, e -> {
            try {
                return accessTokenFor(e);
            } catch (Exception ex) {
                throw new IllegalStateException(ex);
            }
        });
    }

    protected String accessToken(MvcResult result) throws Exception {
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        return body.get("accessToken").asText();
    }

    protected Cookie refreshCookie(MvcResult result) {
        return result.getResponse().getCookie(REFRESH_COOKIE);
    }

    protected static String bearer(String token) {
        return "Bearer " + token;
    }

    // ---- Requests as a demo user -------------------------------------------------

    protected ResultActions getAs(String email, String url, Object... vars) throws Exception {
        return mvc.perform(as(email, get(url, vars)));
    }

    protected ResultActions postAs(String email, Object body, String url, Object... vars) throws Exception {
        return mvc.perform(as(email, post(url, vars)).contentType(MediaType.APPLICATION_JSON).content(json(body)));
    }

    protected ResultActions putAs(String email, Object body, String url, Object... vars) throws Exception {
        return mvc.perform(as(email, put(url, vars)).contentType(MediaType.APPLICATION_JSON).content(json(body)));
    }

    protected ResultActions deleteAs(String email, String url, Object... vars) throws Exception {
        return mvc.perform(as(email, delete(url, vars)));
    }

    protected MockHttpServletRequestBuilder as(String email, MockHttpServletRequestBuilder request) {
        return request.header(HttpHeaders.AUTHORIZATION, bearer(token(email)));
    }

    protected JsonNode body(ResultActions result) throws Exception {
        return objectMapper.readTree(result.andReturn().getResponse().getContentAsString());
    }
}
