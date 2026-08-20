package com.urlshortener.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Smoke check that the `prod` profile document in application.yml binds the expected Postgres
 * properties — property binding only, deliberately WITHOUT starting a real DataSource (no
 * @EnableAutoConfiguration here), so this passes with no live Postgres instance running.
 */
class ProdProfileConfigTest {

    @Configuration
    static class EmptyConfig {
    }

    @Test
    void prodProfileBindsPostgresDatasourceProperties() {
        new ApplicationContextRunner()
                .withInitializer(new ConfigDataApplicationContextInitializer())
                .withPropertyValues("spring.profiles.active=prod")
                .withUserConfiguration(EmptyConfig.class)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    var env = context.getEnvironment();
                    assertThat(env.getProperty("spring.datasource.driver-class-name"))
                            .isEqualTo("org.postgresql.Driver");
                    assertThat(env.getProperty("spring.datasource.url")).contains("postgresql");
                    assertThat(env.getProperty("spring.jpa.hibernate.ddl-auto")).isEqualTo("update");
                });
    }
}
