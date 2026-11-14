package com.jetsetgo.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.junit.jupiter.api.Test;

class RazorpayServiceTest {
    @Test
    void acceptsOnlySignatureForMatchingOrderAndPayment() throws Exception {
        String secret = "test-secret";
        String orderId = "order_123";
        String paymentId = "pay_456";
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        String signature = HexFormat.of().formatHex(
                mac.doFinal((orderId + "|" + paymentId).getBytes(StandardCharsets.UTF_8)));

        assertTrue(RazorpayService.isValidSignature(orderId, paymentId, signature, secret));
        assertFalse(RazorpayService.isValidSignature(orderId, "pay_changed", signature, secret));
        assertFalse(RazorpayService.isValidSignature(orderId, paymentId, "not-a-signature", secret));
        assertFalse(RazorpayService.isValidSignature(orderId, paymentId, null, secret));
    }
}
