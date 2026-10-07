package org.mifos.connector.ams.paygops.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * How this connector reaches the Zeebe broker: {@code zeebe.broker.*} and {@code zeebe.client.*}.
 *
 * <p>
 * {@code zeebe.client.evenly-allocated-max-jobs} is not here: its value in application.yml is a Spring expression,
 * which only {@code @Value} evaluates, so {@code ZeebeWorkers} keeps reading it that way.
 * </p>
 *
 * @param broker
 *            the broker to connect to
 * @param client
 *            client settings
 */
@Validated
@ConfigurationProperties(prefix = "zeebe")
public record ZeebeProperties(@NotNull @Valid Broker broker, @NotNull @Valid Client client) {

    /**
     * The broker to connect to: {@code zeebe.broker.*}.
     *
     * @param contactpoint
     *            gateway address, as {@code host:port}
     */
    public record Broker(@NotNull String contactpoint) {
    }

    /**
     * Client settings: {@code zeebe.client.*}.
     *
     * @param maxExecutionThreads
     *            size of the job worker execution thread pool
     */
    public record Client(@NotNull Integer maxExecutionThreads) {
    }
}
