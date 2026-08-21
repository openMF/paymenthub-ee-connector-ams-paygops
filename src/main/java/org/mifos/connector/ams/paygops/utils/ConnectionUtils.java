package org.mifos.connector.ams.paygops.utils;

public class ConnectionUtils {

    /**
     * returns camel dsl for applying connection timeout
     *
     * @param timeout timeout value in ms
     * @return
     */
    public static String getConnectionTimeoutDsl(int timeout) {
        // camel-http 4 uses Apache HttpClient 5, where the old HttpClient 4 option
        // `socketTimeout` no longer exists. Camel binds every httpClient.* key onto
        // RequestConfig.Builder and then rejects the whole endpoint URI if one key is
        // left over, so with the old name every outgoing call failed with
        // ResolveEndpointFailedException. `responseTimeout` is its replacement.
        String base = "httpClient.connectTimeout={}&httpClient.connectionRequestTimeout={}&httpClient.responseTimeout={}";
        return base.replace("{}", "" + timeout);
    }
}
