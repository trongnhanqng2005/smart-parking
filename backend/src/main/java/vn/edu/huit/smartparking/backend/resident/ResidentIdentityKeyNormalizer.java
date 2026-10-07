package vn.edu.huit.smartparking.backend.resident;

import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.Locale;

public final class ResidentIdentityKeyNormalizer {
    public static final int BUILDING_KEY_MAX_BYTES = 2_048;
    public static final int APARTMENT_CODE_KEY_MAX_BYTES = 768;
    public static final int IDENTITY_NUMBER_KEY_MAX_BYTES = 512;

    private ResidentIdentityKeyNormalizer() {}

    public static byte[] buildingKey(String value) {
        String normalized = trimAndCollapseWhitespace(normalizeNfc(requireValue(value)));
        return utf8(Normalizer.normalize(normalized.toUpperCase(Locale.ROOT), Normalizer.Form.NFC),
                BUILDING_KEY_MAX_BYTES, "building");
    }

    public static byte[] apartmentCodeKey(String value) {
        String normalized = trimWhitespace(normalizeNfc(requireValue(value)));
        return utf8(Normalizer.normalize(normalized.toUpperCase(Locale.ROOT), Normalizer.Form.NFC),
                APARTMENT_CODE_KEY_MAX_BYTES, "apartment_code");
    }

    public static byte[] identityNumberKey(String value) {
        String normalized = removeWhitespace(normalizeNfc(requireValue(value)));
        return utf8(Normalizer.normalize(uppercaseLatinLetters(normalized), Normalizer.Form.NFC),
                IDENTITY_NUMBER_KEY_MAX_BYTES, "identity_number");
    }

    private static String requireValue(String value) {
        if (value == null) {
            throw new IllegalArgumentException("Identity value is required");
        }
        return value;
    }

    private static String normalizeNfc(String value) {
        return Normalizer.normalize(value, Normalizer.Form.NFC);
    }

    private static String trimWhitespace(String value) {
        int start = 0;
        int end = value.length();
        while (start < end) {
            int codePoint = value.codePointAt(start);
            if (!isWhitespace(codePoint)) {
                break;
            }
            start += Character.charCount(codePoint);
        }
        while (start < end) {
            int codePoint = value.codePointBefore(end);
            if (!isWhitespace(codePoint)) {
                break;
            }
            end -= Character.charCount(codePoint);
        }
        String trimmed = value.substring(start, end);
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("Identity value must not be blank");
        }
        return trimmed;
    }

    private static String trimAndCollapseWhitespace(String value) {
        StringBuilder result = new StringBuilder(value.length());
        boolean pendingSpace = false;
        for (int offset = 0; offset < value.length();) {
            int codePoint = value.codePointAt(offset);
            offset += Character.charCount(codePoint);
            if (isWhitespace(codePoint)) {
                if (result.length() > 0) {
                    pendingSpace = true;
                }
                continue;
            }
            if (pendingSpace) {
                result.append(' ');
                pendingSpace = false;
            }
            result.appendCodePoint(codePoint);
        }
        if (result.isEmpty()) {
            throw new IllegalArgumentException("Identity value must not be blank");
        }
        return result.toString();
    }

    private static String removeWhitespace(String value) {
        StringBuilder result = new StringBuilder(value.length());
        for (int offset = 0; offset < value.length();) {
            int codePoint = value.codePointAt(offset);
            offset += Character.charCount(codePoint);
            if (!isWhitespace(codePoint)) {
                result.appendCodePoint(codePoint);
            }
        }
        if (result.isEmpty()) {
            throw new IllegalArgumentException("Identity value must not be blank");
        }
        return result.toString();
    }

    private static String uppercaseLatinLetters(String value) {
        StringBuilder result = new StringBuilder(value.length());
        for (int offset = 0; offset < value.length();) {
            int codePoint = value.codePointAt(offset);
            offset += Character.charCount(codePoint);
            String character = new String(Character.toChars(codePoint));
            if (Character.UnicodeScript.of(codePoint) == Character.UnicodeScript.LATIN) {
                result.append(character.toUpperCase(Locale.ROOT));
            } else {
                result.append(character);
            }
        }
        return result.toString();
    }

    private static boolean isWhitespace(int codePoint) {
        return Character.isWhitespace(codePoint) || Character.isSpaceChar(codePoint);
    }

    private static byte[] utf8(String value, int maxBytes, String field) {
        byte[] result = value.getBytes(StandardCharsets.UTF_8);
        if (result.length > maxBytes) {
            throw new IllegalArgumentException("Canonical " + field + " exceeds its storage capacity");
        }
        return result;
    }
}
