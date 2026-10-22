package com.aitravel.session.profile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.aitravel.session.config.JwtTestSupport;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/** The store is chosen by {@code travel.postgres.enabled}; a database that is down never stops the service. */
class ProfileStoreSelectionTest {

    @Nested
    @SpringBootTest
    @TestPropertySource(properties = {JwtTestSupport.ISSUER_PROPERTY, JwtTestSupport.AUDIENCE_PROPERTY})
    @Import(JwtTestSupport.DefaultJwtMockMvcConfig.class)
    class Default {

        @Autowired
        private ProfileStore store;

        @Test
        void usesInMemoryStore() {
            assertThat(store).isInstanceOf(InMemoryProfileStore.class);
        }
    }

    @Nested
    @SpringBootTest
    @AutoConfigureMockMvc
    @TestPropertySource(properties = {JwtTestSupport.ISSUER_PROPERTY, JwtTestSupport.AUDIENCE_PROPERTY,
            "travel.postgres.enabled=true",
            "travel.postgres.url=jdbc:postgresql://localhost:1/roam",
            "travel.postgres.connect-timeout=1s"})
    @Import(JwtTestSupport.DefaultJwtMockMvcConfig.class)
    class PostgresDown {

        @Autowired
        private ProfileStore store;

        @Autowired
        private MockMvc mockMvc;

        @Test
        void startsAndAnswersProfileCallsWith503() throws Exception {
            assertThat(store).isInstanceOf(PostgresProfileStore.class);

            mockMvc.perform(get("/api/v1/profile").with(jwt()))
                    .andExpect(status().isServiceUnavailable())
                    .andExpect(jsonPath("$.title").value("Profile unavailable"));
        }

        @Test
        void otherEndpointsStillWork() throws Exception {
            mockMvc.perform(get("/api/v1/travel-plan").with(jwt())).andExpect(status().isOk());
        }
    }
}
