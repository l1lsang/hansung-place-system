package com.hansung.hsp.auth;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

public final class PasswordHashTool {
    private PasswordHashTool() {}
    public static void main(String[] args) throws Exception {
        var console = System.console();
        char[] input = console != null ? console.readPassword("Password: ")
                : new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8)).readLine().toCharArray();
        try {
            String password = new String(input);
            if (password.length() < 12 || password.getBytes(StandardCharsets.UTF_8).length > 72) {
                throw new IllegalArgumentException("Use at least 12 characters and at most 72 UTF-8 bytes.");
            }
            System.out.println(new BCryptPasswordEncoder(12).encode(password));
        } finally {
            Arrays.fill(input, '\0');
        }
    }
}

