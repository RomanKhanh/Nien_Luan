package com.brainblocks.backend.service.payment;

import com.brainblocks.backend.entity.Order;
import com.brainblocks.backend.exception.PaymentGatewayException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.RoundingMode;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class MoMoGateway implements PaymentGateway {

    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${momo.partner-code}")
    private String partnerCode;
    @Value("${momo.access-key}")
    private String accessKey;
    @Value("${momo.secret-key}")
    private String secretKey;
    @Value("${momo.endpoint}")
    private String endpoint;
    @Value("${momo.redirect-url}")
    private String redirectUrl;
    @Value("${momo.ipn-url}")
    private String ipnUrl;

    @Override
    public String createPaymentUrl(Order order, String txnRef, String clientIp) {
        String amount = order.getTotalAmount().setScale(0, RoundingMode.HALF_UP).toBigInteger().toString();
        String orderInfo = "Thanh toan don hang " + order.getOrderCode();
        String extraData = "";
        String requestType = "captureWallet";

        String rawSignature = "accessKey=" + accessKey +
                "&amount=" + amount +
                "&extraData=" + extraData +
                "&ipnUrl=" + ipnUrl +
                "&orderId=" + txnRef +
                "&orderInfo=" + orderInfo +
                "&partnerCode=" + partnerCode +
                "&redirectUrl=" + redirectUrl +
                "&requestId=" + txnRef +
                "&requestType=" + requestType;
        String signature = hmacSHA256(secretKey, rawSignature);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("partnerCode", partnerCode);
        body.put("partnerName", "BrainBlocks");
        body.put("storeId", "BrainBlocksStore");
        body.put("requestId", txnRef);
        body.put("amount", amount);
        body.put("orderId", txnRef);
        body.put("orderInfo", orderInfo);
        body.put("redirectUrl", redirectUrl);
        body.put("ipnUrl", ipnUrl);
        body.put("lang", "vi");
        body.put("extraData", extraData);
        body.put("requestType", requestType);
        body.put("signature", signature);

        try {
            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(endpoint))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body), StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());

            Map<?, ?> result = objectMapper.readValue(response.body(), Map.class);
            if (!"0".equals(String.valueOf(result.get("resultCode")))) {
                throw new PaymentGatewayException("MoMo rejected the payment request: " + result.get("message"));
            }
            return (String) result.get("payUrl");
        } catch (PaymentGatewayException e) {
            throw e;
        } catch (Exception e) {
            throw new PaymentGatewayException("Cannot reach MoMo payment gateway", e);
        }
    }

    @Override
    public boolean verifySignature(Map<String, String> params) {
        String receivedSignature = params.get("signature");
        if (receivedSignature == null) return false;

        String rawSignature = "accessKey=" + accessKey +
                "&amount=" + params.getOrDefault("amount", "") +
                "&extraData=" + params.getOrDefault("extraData", "") +
                "&message=" + params.getOrDefault("message", "") +
                "&orderId=" + params.getOrDefault("orderId", "") +
                "&orderInfo=" + params.getOrDefault("orderInfo", "") +
                "&orderType=" + params.getOrDefault("orderType", "") +
                "&partnerCode=" + params.getOrDefault("partnerCode", "") +
                "&payType=" + params.getOrDefault("payType", "") +
                "&requestId=" + params.getOrDefault("requestId", "") +
                "&responseTime=" + params.getOrDefault("responseTime", "") +
                "&resultCode=" + params.getOrDefault("resultCode", "") +
                "&transId=" + params.getOrDefault("transId", "");
        return receivedSignature.equalsIgnoreCase(hmacSHA256(secretKey, rawSignature));
    }

    @Override
    public boolean isSuccess(Map<String, String> params) {
        return "0".equals(params.get("resultCode"));
    }

    @Override
    public String extractTransactionId(Map<String, String> params) {
        return params.get("transId");
    }

    @Override
    public String extractTxnRef(Map<String, String> params) {
        return params.get("orderId");
    }

    private String hmacSHA256(String key, String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] result = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : result) hex.append(String.format("%02x", b));
            return hex.toString();
        } catch (Exception e) {
            throw new IllegalStateException("Cannot sign MoMo data", e);
        }
    }
}
