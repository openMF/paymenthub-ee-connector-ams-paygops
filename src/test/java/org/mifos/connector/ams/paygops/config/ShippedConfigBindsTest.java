package org.mifos.connector.ams.paygops.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.autoconfigure.validation.ValidationAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

/**
 * Every property these records ask for has to be there, or the connector must refuse to start and say which one is
 * missing. That is what the plain {@code @Value} declarations did before they were replaced, so these tests hold the
 * replacement to the same promise, and they read the real application.yml rather than a copy of it.
 *
 * <p>
 * The missing-section check runs with no configuration file at all. Loading only this module's application.yml is not
 * enough to remove a section, because paymenthub-ee-core puts its own application.yaml on the classpath and that one
 * also sets {@code zeebe.*}.
 * </p>
 */
class ShippedConfigBindsTest {

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties({ ConnectorCamelProperties.class, ZeebeProperties.class, PaygopsProperties.class,
            AmsProperties.class })
    static class AllRecords {}

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(ConnectorCamelProperties.class)
    static class OnlyCamel {}

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(ZeebeProperties.class)
    static class OnlyZeebe {}

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(PaygopsProperties.class)
    static class OnlyPaygops {}

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(AmsProperties.class)
    static class OnlyAms {}

    /** One configuration class per record, keyed by the prefix that record binds. */
    private static final Map<String, Class<?>> ONE_RECORD_EACH = Map.of("camel", OnlyCamel.class, "zeebe", OnlyZeebe.class, "paygops",
            OnlyPaygops.class, "ams", OnlyAms.class);

    private ApplicationContextRunner runner() {
        return new ApplicationContextRunner().withConfiguration(
                AutoConfigurations.of(ConfigurationPropertiesAutoConfiguration.class, ValidationAutoConfiguration.class));
    }

    private ApplicationContextRunner withShippedYaml() {
        return runner().withInitializer(new ConfigDataApplicationContextInitializer());
    }

    @Test
    void theShippedApplicationYmlFillsEveryField() {
        withShippedYaml().withUserConfiguration(AllRecords.class).run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(ConnectorCamelProperties.class).serverPort()).isEqualTo(5000);
            ZeebeProperties zeebe = context.getBean(ZeebeProperties.class);
            assertThat(zeebe.broker().contactpoint()).isEqualTo("localhost:26500");
            assertThat(zeebe.client().maxExecutionThreads()).isEqualTo(100);
            PaygopsProperties paygops = context.getBean(PaygopsProperties.class);
            assertThat(paygops.baseUrl()).isNotEmpty();
            assertThat(paygops.endpoint().verification()).isEqualTo("api/v1/payments/validate");
            assertThat(paygops.endpoint().confirmation()).isEqualTo("api/v1/payments");
            assertThat(context.getBean(AmsProperties.class).timeout()).isEqualTo(60000);
        });
    }

    @Test
    void everyRecordRefusesToStartWhenItsSectionIsMissing() {
        ONE_RECORD_EACH.forEach((prefix, configuration) -> runner().withUserConfiguration(configuration).run(context -> {
            assertThat(context).as("context with nothing configured under '%s'", prefix).hasFailed();
            assertThat(context.getStartupFailure()).as("failure for '%s'", prefix).hasStackTraceContaining("BindValidationException")
                    .hasStackTraceContaining("Binding validation errors on " + prefix);
        }));
    }

    @Test
    void aValueSetToNothingOnANumberFieldStopsStartup() {
        withShippedYaml().withUserConfiguration(OnlyAms.class).withPropertyValues("ams.timeout=").run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure()).hasStackTraceContaining("Binding validation errors on ams");
        });
    }

    @Test
    void aValueSetToNothingOnAStringFieldIsAcceptedJustAsItWasBefore() {
        withShippedYaml().withUserConfiguration(OnlyPaygops.class).withPropertyValues("paygops.endpoint.verification=")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean(PaygopsProperties.class).endpoint().verification()).isEmpty();
                });
    }
}
