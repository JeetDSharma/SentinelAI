package com.example.sentinelai;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;

/**
 * Full application context against real PostgreSQL + Elasticsearch containers.
 *
 * <p>Every *IT class uses exactly this annotation so Spring's test-context cache (keyed by configuration)
 * reuses ONE context — and therefore one pair of containers — across all integration test classes.
 * Adding a different annotation or property to one class would silently start a second context.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
public @interface IntegrationTest {
}
