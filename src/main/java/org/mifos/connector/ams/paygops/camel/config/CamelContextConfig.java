package org.mifos.connector.ams.paygops.camel.config;

import org.apache.camel.CamelContext;
import org.apache.camel.spi.RestConfiguration;
import org.apache.camel.spring.boot.CamelContextConfiguration;
import org.mifos.connector.ams.paygops.config.ConnectorCamelProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;

@Configuration
public class CamelContextConfig {

    private final int serverPort;

    public CamelContextConfig(ConnectorCamelProperties camelProperties) {
        this.serverPort = camelProperties.serverPort();
    }

    @Bean
    CamelContextConfiguration contextConfiguration() {
        return new CamelContextConfiguration() {
            @Override
            public void beforeApplicationStart(CamelContext camelContext) {
                camelContext.setTracing(false);
                camelContext.setMessageHistory(false);
                camelContext.setStreamCaching(true);
                camelContext.disableJMX();

                RestConfiguration rest = new RestConfiguration();
                camelContext.setRestConfiguration(rest);
                rest.setComponent("undertow");
                rest.setProducerComponent("undertow");
                rest.setPort(serverPort);
                rest.setBindingMode(RestConfiguration.RestBindingMode.json);
                rest.setDataFormatProperties(new HashMap<>());
                rest.getDataFormatProperties().put("prettyPrint", "true");
                rest.setScheme("http");
            }

            @Override
            public void afterApplicationStart(CamelContext camelContext) {
                // empty
            }
        };
    }
}
