package com.seckill.util;

import org.springframework.util.DigestUtils;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

public final class PasswordUtil {

    private PasswordUtil() {
    }

    public static String generateSalt() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    public static String encrypt(String password, String salt) {
        return DigestUtils.md5DigestAsHex((password + salt).getBytes(StandardCharsets.UTF_8));
    }

    public static boolean verify(String rawPassword, String salt, String encrypted) {
        return encrypt(rawPassword, salt).equals(encrypted);
    }
}
