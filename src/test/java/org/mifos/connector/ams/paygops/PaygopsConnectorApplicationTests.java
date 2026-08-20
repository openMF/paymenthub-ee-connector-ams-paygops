package org.mifos.connector.ams.paygops;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

// paygops.authheader has no default, so the context needs it supplied here.
@SpringBootTest(properties = "paygops.authheader=test-token")
class PaygopsConnectorApplicationTests {

    @Test
    void contextLoads() {
    }

}
