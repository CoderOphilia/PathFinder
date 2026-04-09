package com.pathfinder.web;

import com.pathfinder.auth.config.SecurityConfig;
import com.pathfinder.auth.config.SessionRoleAuthenticationFilter;
import com.pathfinder.landing.web.LandingController;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@WebMvcTest(LandingController.class)
@ActiveProfiles("prod")
@Import({SecurityConfig.class, SessionRoleAuthenticationFilter.class})
class LayoutModelAttributesAdviceProdProfileWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void productionProfileStillProvidesCurrentUrl() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(view().name("layout"))
                .andExpect(model().attribute("currentUrl", "/"));
    }
}
