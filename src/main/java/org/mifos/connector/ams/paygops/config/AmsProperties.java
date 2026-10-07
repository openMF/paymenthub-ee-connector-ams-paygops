package org.mifos.connector.ams.paygops.config;

import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * The {@code ams.*} settings.
 *
 * <p>
 * {@code ams.local.enabled} is not here. It was read as {@code @Value("${ams.local.enabled:false}")}: a missing key
 * means {@code false}, but an empty one stops startup. A record cannot do both, because an empty value binds to
 * {@code null} and a default would then turn it into {@code false}. So {@code ZeebeWorkers} keeps that {@code @Value}.
 * </p>
 *
 * @param timeout
 *            connection timeout, in milliseconds, for the PayGOPS calls
 */
@Validated
@ConfigurationProperties(prefix = "ams")
public record AmsProperties(@NotNull Integer timeout) {
}
