package com.stockit.backend.feature.auth.support;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

public final class BcryptPasswordGenerator {

    private BcryptPasswordGenerator() {
    }

    public static void main(String[] args) {
        if (args.length != 1 || args[0].isBlank()) {
            throw new IllegalArgumentException("A non-blank password is required");
        }
        System.out.println(new BCryptPasswordEncoder(12).encode(args[0]));
    }
}
