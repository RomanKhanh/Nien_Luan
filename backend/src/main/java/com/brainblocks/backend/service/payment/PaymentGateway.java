package com.brainblocks.backend.service.payment;

import com.brainblocks.backend.entity.Order;

import java.util.Map;

public interface PaymentGateway {
    String createPaymentUrl(Order order, String txnRef, String clientIp);
    boolean verifySignature(Map<String, String> params);
    boolean isSuccess(Map<String, String> params);
    String extractTransactionId(Map<String, String> params);
    String extractTxnRef(Map<String, String> params);
}
