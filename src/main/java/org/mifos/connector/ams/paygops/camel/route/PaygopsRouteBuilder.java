package org.mifos.connector.ams.paygops.camel.route;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.camel.Exchange;
import org.apache.camel.LoggingLevel;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.model.dataformat.JsonLibrary;
import org.json.JSONObject;
import org.mifos.connector.ams.paygops.paygopsDTO.PaygopsRequestDTO;
import org.mifos.connector.ams.paygops.paygopsDTO.PaygopsResponseDto;
import org.mifos.connector.ams.paygops.utils.ConnectionUtils;
import org.mifos.connector.ams.paygops.utils.ErrorCodeEnum;
import org.mifos.connector.ams.paygops.utils.PayloadUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.UUID;

import static org.mifos.connector.ams.paygops.camel.config.CamelProperties.*;
import static org.mifos.connector.ams.paygops.camel.config.CamelProperties.AMS_REQUEST;
import static org.mifos.connector.ams.paygops.zeebe.ZeebeVariables.*;

@Component
public class PaygopsRouteBuilder extends RouteBuilder {

    Logger logger = LoggerFactory.getLogger(this.getClass());

    // The application configures its own ObjectMapper (NON_NULL, JavaTimeModule,
    // FAIL_ON_UNKNOWN_PROPERTIES off). Use that one instead of a fresh default.
    @Autowired
    private ObjectMapper objectMapper;

    @Value("${paygops.base-url}")
    private String paygopsBaseUrl;

    @Value("${paygops.endpoint.verification}")
    private String verificationEndpoint;

    @Value("${paygops.endpoint.confirmation}")
    private String confirmationEndpoint;

    @Value("${paygops.auth-header}")
    private String accessToken;

    @Value("${ams.timeout}")
    private Integer amsTimeout;

    enum accountStatus{
        ACTIVE,
        REJECTED
    }


    public PaygopsRouteBuilder() {

    }


    @Override
    public void configure() {

        from("rest:POST:/api/v1/payments/validate")
                .process(exchange -> {
                    JSONObject channelRequest = new JSONObject(exchange.getIn().getBody(String.class));
                    exchange.setProperty(CHANNEL_REQUEST, channelRequest);
                    exchange.setProperty(TRANSACTION_ID, transactionIdFrom(channelRequest));
                })
                .to("direct:transfer-validation-base");

        from("rest:POST:/api/paymentHub/Confirmation")
                .process(exchange -> {
                    JSONObject channelRequest = new JSONObject(exchange.getIn().getBody(String.class));
                    exchange.setProperty(CHANNEL_REQUEST, channelRequest);
                    exchange.setProperty(TRANSACTION_ID, transactionIdFrom(channelRequest));
                })
                .to("direct:transfer-settlement-base");

        from("direct:transfer-validation-base")
                .id("transfer-validation-base")
                .log(LoggingLevel.INFO, "## Starting Paygops Validation base route")
                .to("direct:transfer-validation")
                .choice()
                .when(header("CamelHttpResponseCode").isEqualTo("200"))
                .log(LoggingLevel.INFO, "Paygops Validation Response Received")
                .unmarshal().json(JsonLibrary.Jackson, PaygopsResponseDto.class)
                .process(exchange -> {
                    // processing success case
                    try {
                        PaygopsResponseDto result = exchange.getIn().getBody(PaygopsResponseDto.class);
                        if (result.getReconciled()) {
                            logger.info("Paygops Validation Successful");
                            exchange.setProperty(PARTY_LOOKUP_FAILED, false);
                            exchange.setProperty("accountStatus",accountStatus.ACTIVE.toString());
                            exchange.setProperty("subStatus", "");
                            exchange.setProperty("accountHoldingInstitutionId", exchange.getProperty("accountHoldingInstitutionId"));
                            exchange.setProperty(TRANSACTION_ID, exchange.getProperty(TRANSACTION_ID));
                            exchange.setProperty("amount", result.getAmount());
                            exchange.setProperty("currency", result.getCurrency());
                            exchange.setProperty("msisdn", result.getSender_phone_number().substring(1));
                        } else {
                            setErrorCamelInfo(exchange,"Validation Unsuccessful: Reconciled field returned false",
                                    ErrorCodeEnum.RECONCILIATION.getCode(), result.toString());

                            exchange.setProperty(PARTY_LOOKUP_FAILED, true);
                            exchange.setProperty("accountStatus",accountStatus.REJECTED.toString());
                            exchange.setProperty("subStatus", "");
                        }
                    } catch (Exception e) {
                        logger.error("Body could not be passed due to : {} ", String.valueOf(e));
                        setErrorCamelInfo(exchange,"Body data could not be parsed,setting validation as failed",
                                ErrorCodeEnum.DEFAULT.getCode(),exchange.getIn().getBody(String.class));
                        exchange.setProperty(PARTY_LOOKUP_FAILED, true);
                        exchange.setProperty("accountStatus",accountStatus.REJECTED.toString());
                        exchange.setProperty("subStatus", "");
                    }
                })
                .otherwise()
                .log(LoggingLevel.ERROR, "Paygops Validation unsuccessful")
                .process(exchange -> {
                    // Set the outcome first, then try to describe it. The body is not
                    // guaranteed to be the PaygOps error shape: a gateway can answer with
                    // HTML and a 401 comes back with different JSON. If parsing threw here
                    // the flag stayed unset, and the Zeebe worker then unboxed a null
                    // property and the job was never completed.
                    exchange.setProperty(PARTY_LOOKUP_FAILED, true);
                    describeErrorFromBody(exchange);
                });

        from("direct:transfer-validation")
                .id("transfer-validation")
                .log(LoggingLevel.INFO, "## Starting Paygops Validation route")
                .removeHeader("*")
                .setHeader(Exchange.HTTP_METHOD, constant("POST"))
                .setHeader("Authorization", simple("Bearer "+ accessToken))
                .setHeader("Content-Type", constant("application/json"))
                .setHeader("Accept-Encoding", constant("gzip;q=1.0, identity; q=0.5"))
                .setBody(exchange -> {
                    if(exchange.getProperty(CHANNEL_REQUEST) != null)
                    {
                        JSONObject channelRequest = (JSONObject) exchange.getProperty(CHANNEL_REQUEST);
                        String transactionId = exchange.getProperty(TRANSACTION_ID, String.class);
                        PaygopsRequestDTO verificationRequestDTO = getPaygopsDtoFromChannelRequest(channelRequest,
                                transactionId);
                        logger.debug("Validation request DTO: \n\n\n" + verificationRequestDTO);
                        return verificationRequestDTO;
                    }
                    else {
                        JSONObject paybillRequest = new JSONObject(exchange.getIn().getBody(String.class));
                        PaygopsRequestDTO paygopsRequestDTO = PayloadUtils.convertPaybillPayloadToAmsPaygopsPayload(paybillRequest);
                        log.debug(paygopsRequestDTO.toString());
                        exchange.setProperty(TRANSACTION_ID, paygopsRequestDTO.getTransactionId());
                        exchange.setProperty("accountHoldingInstitutionId", exchange.getProperty("accountHoldingInstitutionId"));
                        logger.debug("Validation request DTO: \n\n\n" + paygopsRequestDTO);
                        return paygopsRequestDTO;
                    }
                })
                .marshal().json(JsonLibrary.Jackson)
                .toD(getVerificationEndpoint() + "?bridgeEndpoint=true&throwExceptionOnFailure=false&"+
                        ConnectionUtils.getConnectionTimeoutDsl(amsTimeout))
                .log(LoggingLevel.TRACE, "Paygops validation api response: \n\n..\n\n..\n\n.. ${body}");

        from("direct:transfer-settlement-base")
                .id("transfer-settlement-base")
                .log(LoggingLevel.INFO, "## Transfer Settlement route")
                .to("direct:transfer-settlement")
                .choice()
                .when(header("CamelHttpResponseCode").startsWith("2"))
                .log(LoggingLevel.INFO, "Settlement Response Received")
                .process(exchange -> {
                    // processing success case
                    try {
                        String body = exchange.getIn().getBody(String.class);
                        PaygopsResponseDto result = objectMapper.readValue(body, PaygopsResponseDto.class);
                        if (result.getReception_datetime()!=null) {
                            logger.info("Paygops Settlement Successful");
                            exchange.setProperty(TRANSFER_SETTLEMENT_FAILED, false);
                        } else {
                            setErrorCamelInfo(exchange,"Settlement Unsuccessful: Response did not contain reception date",
                                    ErrorCodeEnum.RECONCILIATION.getCode(), result.toString());

                            exchange.setProperty(TRANSFER_SETTLEMENT_FAILED, true);
                        }
                    } catch (Exception e) {
                        logger.error("Body could not be passed due to : {} ", String.valueOf(e));
                        setErrorCamelInfo(exchange,"Body data could not be parsed,setting confirmation as failed",
                                ErrorCodeEnum.DEFAULT.getCode(), exchange.getIn().getBody(String.class));
                        exchange.setProperty(TRANSFER_SETTLEMENT_FAILED, true);
                        exchange.setProperty("accountStatus",accountStatus.REJECTED.toString());
                        exchange.setProperty("subStatus", "");
                    }


                })
                .otherwise()
                .log(LoggingLevel.ERROR, "Settlement unsuccessful")
                .process(exchange -> {
                    // Same as the validation branch: outcome first, description after.
                    exchange.setProperty(TRANSFER_SETTLEMENT_FAILED, true);
                    describeErrorFromBody(exchange);
                });

        from("rest:POST:/api/v1/paybill/validate/paygops")
                .id("validate-user")
                .log(LoggingLevel.INFO, "## Paygops user validation")
                .setBody(e -> {
                    String body=e.getIn().getBody(String.class);
                    String accountHoldingInstitutionId= String.valueOf(e.getIn().getHeader("accountHoldingInstitutionId"));
                    e.setProperty("accountHoldingInstitutionId",accountHoldingInstitutionId);
                    logger.debug("Body : {}",body);
                    logger.debug("accountHoldingInstitutionId : {}",accountHoldingInstitutionId);
                    return body;
                })
                .to("direct:transfer-validation-base")
                .process(e->{
                    logger.debug("Response received from validation base : {}",e.getIn().getBody());
                    // Building the response
                    JSONObject responseObject=new JSONObject();
                    responseObject.put("reconciled", e.getProperty(PARTY_LOOKUP_FAILED).equals(false));
                    responseObject.put("amsName", "paygops");
                    responseObject.put("accountHoldingInstitutionId", e.getProperty("accountHoldingInstitutionId"));
                    responseObject.put(TRANSACTION_ID, e.getProperty(TRANSACTION_ID));
                    responseObject.put("amount", e.getProperty("amount"));
                    responseObject.put("currency", e.getProperty("currency"));
                    responseObject.put("msisdn", e.getProperty("msisdn"));
                    logger.debug("response object :{}",responseObject);
                    e.getIn().setBody(responseObject.toString());
                });

        from("direct:transfer-settlement")
                .id("transfer-settlement")
                .log(LoggingLevel.INFO, "## Starting transfer settlement route")
                .removeHeader("*")
                .setHeader(Exchange.HTTP_METHOD, constant("POST"))
                .setHeader("Authorization", simple("Bearer "+ accessToken))
                .setHeader("Content-Type", constant("application/json"))
                .setBody(exchange -> {
                    JSONObject channelRequest = (JSONObject) exchange.getProperty(CHANNEL_REQUEST);
                    String transactionId = exchange.getProperty(TRANSACTION_ID, String.class);
                    PaygopsRequestDTO confirmationRequestDTO = getPaygopsDtoFromChannelRequest(channelRequest,
                            transactionId);
                    logger.debug("Confirmation request DTO: {}",confirmationRequestDTO);
                    exchange.setProperty(AMS_REQUEST,confirmationRequestDTO.toString());
                    return confirmationRequestDTO;
                })
                .marshal().json(JsonLibrary.Jackson)
                .toD(getConfirmationEndpoint() + "?bridgeEndpoint=true&throwExceptionOnFailure=false&" +
                        ConnectionUtils.getConnectionTimeoutDsl(amsTimeout))
                .log(LoggingLevel.TRACE, "Paygops verification api response: \n ${body}");

    }


    // returns the complete URL for verification request
    private String getVerificationEndpoint() {
        return paygopsBaseUrl + verificationEndpoint;
    }

    // returns the complete URL for confirmation request
    private String getConfirmationEndpoint() {
        return paygopsBaseUrl + confirmationEndpoint;
    }

    private PaygopsRequestDTO getPaygopsDtoFromChannelRequest(JSONObject channelRequest, String transactionId) {
        PaygopsRequestDTO verificationRequestDTO = new PaygopsRequestDTO();

        String phoneNumber = channelRequest.getJSONObject("payer")
                .getJSONObject("partyIdInfo").getString("partyIdentifier");
        String memoId = channelRequest.getJSONObject("payee")
                .getJSONObject("partyIdInfo").getString("partyIdentifier"); // instead of account id this value corresponds to national id
        JSONObject amountJson = channelRequest.getJSONObject("amount");
        String operatorName = "MPESA";


        BigDecimal amount = amountJson.getBigDecimal("amount");
        String currency = amountJson.getString("currency");
        String country = "KE";

        verificationRequestDTO.setTransactionId(transactionId);
        verificationRequestDTO.setAmount(amount);
        verificationRequestDTO.setPhoneNumber(phoneNumber);
        verificationRequestDTO.setCurrency(currency);
        verificationRequestDTO.setOperator(operatorName);
        verificationRequestDTO.setMemo(memoId);
        verificationRequestDTO.setCountry(country);
        verificationRequestDTO.setWalletName(phoneNumber);

        return verificationRequestDTO;
    }

    // The channel request does not always carry a transaction id. This used to be
    // the literal "123", so every payment reached PaygOps under the same id.
    private static String transactionIdFrom(JSONObject channelRequest) {
        String transactionId = channelRequest.optString(TRANSACTION_ID, "");
        return transactionId.isEmpty() ? UUID.randomUUID().toString() : transactionId;
    }

    // Reads the PaygOps error shape when the body has it, and still records
    // something useful when it does not.
    private void describeErrorFromBody(Exchange exchange) {
        String body = exchange.getIn().getBody(String.class);
        try {
            JSONObject jsonObject = new JSONObject(body);
            setErrorCamelInfo(exchange, jsonObject.getString("error_message"),
                    jsonObject.getInt("error"), jsonObject.toString(1));
        } catch (Exception e) {
            logger.error("Paygops error response could not be parsed due to : {} ", String.valueOf(e));
            setErrorCamelInfo(exchange, "Paygops call failed and the error body could not be parsed",
                    ErrorCodeEnum.DEFAULT.getCode(), body == null ? "" : body);
        }
    }

    private void setErrorCamelInfo(Exchange exchange, String errorDesc, Integer errorCode, String errorInfo) {
        logger.debug(errorInfo);
        exchange.setProperty(ERROR_CODE, errorCode);
        exchange.setProperty(ERROR_INFORMATION, errorInfo);
        exchange.setProperty(ERROR_DESCRIPTION, errorDesc);

    }
}
