package com.p2pconnect.mod.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Tiny helper for hashing passwords with SHA-256 so plaintext passwords are
 * never stored on disk or sent over MQTT.
 *
 * Honest scope note: this gates the local "Manage Server" panel and is used
 * as a hint when comparing an incoming join request's password field. It is
 * NOT a cryptographic access-control system for the Minecraft connection
 * itself - the public MQTT broker is unauthenticated and unencrypted, and
 * the bore.pub tunnel is just a plain TCP forward. Treat this password as a
 * social/UI-level gate, not a security boundary.
 */
public class AdminAuth {

    public static String sha256Hex(String input) {
        if (input == null) input = "";
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) sb.append('0');
                sb.append(hex);
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is guaranteed to be available on every JVM; this never actually happens.
            throw new IllegalStateException(e);
        }
    }

    public static boolean matches(String plainInput, String storedHash) {
        if (plainInput == null) plainInput = "";
        if (storedHash == null) storedHash = "";
        return sha256Hex(plainInput).equals(storedHash);
    }
}
