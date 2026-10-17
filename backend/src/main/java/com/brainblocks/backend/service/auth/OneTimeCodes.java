package com.brainblocks.backend.service.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.HexFormat;

/**
 * Mã 6 số gửi qua email (mã xác nhận đăng ký, mã đăng nhập): sinh bằng SecureRandom, chỉ giữ bản băm SHA-256.
 */
final class OneTimeCodes {
    private static final SecureRandom RANDOM = new SecureRandom();

    private OneTimeCodes() {
    }

    static String generate() {
        return "%06d".formatted(RANDOM.nextInt(1_000_000));
    }

    // băm kèm email để mã giống nhau ở 2 email khác nhau cho 2 giá trị khác nhau
    static String hash(String email, String code) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest((email + ":" + code).getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    // so sánh thời gian hằng định, không để lộ mã đúng tới ký tự nào qua thời gian phản hồi
    static boolean matches(String expectedHash, String givenHash) {
        return givenHash != null && MessageDigest.isEqual(expectedHash.getBytes(StandardCharsets.UTF_8),
                givenHash.getBytes(StandardCharsets.UTF_8));
    }
}
