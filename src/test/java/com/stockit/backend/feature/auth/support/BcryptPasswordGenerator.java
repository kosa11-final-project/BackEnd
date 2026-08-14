package com.stockit.backend.feature.auth.support;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

public final class BcryptPasswordGenerator {

    private BcryptPasswordGenerator() {
    }

    public static void main(String[] args) throws IOException {
        System.err.print("Password: ");
        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
        String password = reader.readLine();

        if (password == null || password.isBlank()) {
            throw new IllegalArgumentException("A non-blank password is required");
        }
        System.out.println(new BCryptPasswordEncoder(12).encode(password));
    }
}
