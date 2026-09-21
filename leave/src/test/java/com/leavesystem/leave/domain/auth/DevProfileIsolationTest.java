package com.leavesystem.leave.domain.auth;

import com.leavesystem.leave.common.config.InitialEmployeeSeeder;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;

class DevProfileIsolationTest {

    @ParameterizedTest
    @ValueSource(strings = {"prod", "dev,prod", "local"})
    void testAccountsAndFormConfigurationAreNotLoadedOutsideDev(String profiles) {
        try (var context = new AnnotationConfigApplicationContext()) {
            context.getEnvironment().setActiveProfiles(profiles.split(","));
            context.register(DevSecurityConfig.class, DevAuthController.class, InitialEmployeeSeeder.class);
            context.refresh();
            assertThat(context.getBeansOfType(DevSecurityConfig.class)).isEmpty();
            assertThat(context.getBeansOfType(DevAuthController.class)).isEmpty();
            assertThat(context.getBeansOfType(InitialEmployeeSeeder.class)).isEmpty();
        }
    }
}
