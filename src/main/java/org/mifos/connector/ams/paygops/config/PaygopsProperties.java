package org.mifos.connector.ams.paygops.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * How this connector reaches the PayGOPS API: {@code paygops.*}.
 *
 * <p>
 * The token, {@code paygops.authheader}, is not here. application.yml sets it to {@code ${PAYGOPS_AUTHHEADER}} with no
 * default, so that the connector refuses to start without it. A record would not do that: when the variable is missing,
 * the binder keeps the text {@code ${PAYGOPS_AUTHHEADER}} as the value and the connector starts. So
 * {@code PaygopsRouteBuilder} keeps reading the token through {@code @Value}, which fails on a missing variable.
 * </p>
 *
 * @param baseUrl
 *            scheme, host and base path of the PayGOPS API
 * @param endpoint
 *            the two payment endpoints, relative to {@code baseUrl}
 */
@Validated
@ConfigurationProperties(prefix = "paygops")
public record PaygopsProperties(@NotNull String baseUrl, @NotNull @Valid Endpoint endpoint) {

    /**
     * {@code paygops.endpoint.*}.
     *
     * @param verification
     *            path of the payment validation call
     * @param confirmation
     *            path of the payment confirmation call
     */
    public record Endpoint(@NotNull String verification, @NotNull String confirmation) {
    }
}
