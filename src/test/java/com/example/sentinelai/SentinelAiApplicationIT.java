package com.example.sentinelai;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.health.actuate.endpoint.HealthEndpoint;
import org.springframework.boot.health.contributor.Status;
import org.springframework.jdbc.core.JdbcTemplate;

/** Foundation check: the app boots, Flyway applies the schema, and both backing stores report UP. */
@IntegrationTest
class SentinelAiApplicationIT {

    @Autowired JdbcTemplate jdbc;
    @Autowired HealthEndpoint health;

    @Test
    void flywayCreatesSchema() {
        List<String> tables = jdbc.queryForList(
                "SELECT table_name FROM information_schema.tables WHERE table_schema = 'public'", String.class);
        assertThat(tables).contains("screening_case", "case_candidate", "review_decision", "flyway_schema_history");
        assertThat(jdbc.queryForObject("SELECT max(version) FROM flyway_schema_history", String.class)).isEqualTo("2");
    }

    @Test
    void databaseAndElasticsearchAreHealthy() {
        assertThat(health.healthForPath("db").getStatus()).isEqualTo(Status.UP);
        assertThat(health.healthForPath("elasticsearch").getStatus()).isEqualTo(Status.UP);
    }
}
