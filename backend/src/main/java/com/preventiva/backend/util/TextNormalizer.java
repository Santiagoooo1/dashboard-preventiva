package com.preventiva.backend.util;

import java.text.Normalizer;

public class TextNormalizer {

    private TextNormalizer() {
    }

    public static String normalize(String text) {
        if (text == null) {
            return "";
        }

        String normalized = Normalizer.normalize(text, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");

        return normalized
                .trim()
                .replaceAll("\\s+", " ")
                .toUpperCase();
    }
}