package org.mifos.connector.ams.paygops.zeebe;

import io.camunda.zeebe.client.ZeebeClient;
import org.mifos.connector.ams.paygops.config.ZeebeProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ZeebeClientConfiguration {

    private final String zeebeBrokerContactpoint;

    private final int zeebeClientMaxThreads;

    public ZeebeClientConfiguration(ZeebeProperties zeebeProperties) {
        this.zeebeBrokerContactpoint = zeebeProperties.broker().contactpoint();
        this.zeebeClientMaxThreads = zeebeProperties.client().maxExecutionThreads();
    }

    @Bean
    public ZeebeClient setup() {
        return ZeebeClient.newClientBuilder()
                .gatewayAddress(zeebeBrokerContactpoint)
                .usePlaintext()
                .numJobWorkerExecutionThreads(zeebeClientMaxThreads)
                .build();
    }
}
