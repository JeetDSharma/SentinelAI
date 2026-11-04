package com.example.sentinelai.search;

import co.elastic.clients.transport.rest5_client.low_level.Rest5ClientBuilder;
import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.core5.util.TimeValue;
import org.springframework.boot.elasticsearch.autoconfigure.Rest5ClientBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Tweaks Boot's auto-configured Elasticsearch HTTP client (Apache HttpClient 5 underneath) instead of replacing it.
 *
 * <p>Problem observed in the demo: after Elasticsearch restarts, pooled keep-alive connections are dead, and the
 * first request(s) fail with "Connection reset". Retrying once was not enough — the pool held several dead
 * connections. Validating a connection that has been idle for more than 1 s before reusing it fixes the cause.
 */
@Configuration(proxyBeanMethods = false)
class ElasticsearchClientConfig {

    @Bean
    Rest5ClientBuilderCustomizer validateIdleConnections() {
        return new Rest5ClientBuilderCustomizer() {
            @Override
            public void customize(Rest5ClientBuilder builder) {
                // no builder-level changes
            }

            @Override
            public void customize(ConnectionConfig.Builder connectionConfig) {
                connectionConfig.setValidateAfterInactivity(TimeValue.ofSeconds(1));
            }
        };
    }
}
