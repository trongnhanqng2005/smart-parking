package vn.edu.huit.smartparking.backend.resident;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import org.junit.jupiter.api.Test;

class ResidentIdentityKeyNormalizerTests {
    @Test
    void buildingKeyNormalizesNfcWhitespaceAndUnicodeCase() {
        assertArrayEquals(
                "CAFÉ DE PARIS".getBytes(StandardCharsets.UTF_8),
                ResidentIdentityKeyNormalizer.buildingKey("  Cafe\u0301\t \u00a0de\tParis  "));
    }

    @Test
    void buildingUppercaseDoesNotDependOnTheJvmDefaultLocale() {
        Locale previous = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            assertArrayEquals(
                    "I".getBytes(StandardCharsets.UTF_8),
                    ResidentIdentityKeyNormalizer.buildingKey("i"));
            assertArrayEquals(
                    "I-1".getBytes(StandardCharsets.UTF_8),
                    ResidentIdentityKeyNormalizer.apartmentCodeKey("i-1"));
            assertArrayEquals(
                    "I1".getBytes(StandardCharsets.UTF_8),
                    ResidentIdentityKeyNormalizer.identityNumberKey("i 1"));
        } finally {
            Locale.setDefault(previous);
        }
    }

    @Test
    void apartmentCodeKeyPreservesInternalWhitespaceAndPunctuationWithoutCompatibilityFolding() {
        byte[] codeWithDoubleSpace = ResidentIdentityKeyNormalizer.apartmentCodeKey("  a-  01 ");

        assertArrayEquals("A-  01".getBytes(StandardCharsets.UTF_8), codeWithDoubleSpace);
        assertNotEquals(
                java.util.Arrays.toString(codeWithDoubleSpace),
                java.util.Arrays.toString(ResidentIdentityKeyNormalizer.apartmentCodeKey("A- 01")));
        assertNotEquals(
                java.util.Arrays.toString(ResidentIdentityKeyNormalizer.apartmentCodeKey("Ａ-01")),
                java.util.Arrays.toString(ResidentIdentityKeyNormalizer.apartmentCodeKey("A-01")));
    }

    @Test
    void identityNumberRemovesUnicodeWhitespaceAndUppercasesLatinOnly() {
        assertArrayEquals(
                "ABÇ-α１２".getBytes(StandardCharsets.UTF_8),
                ResidentIdentityKeyNormalizer.identityNumberKey(" \tab\u2003ç-α１２\u00a0 "));
    }

    @Test
    void rejectsCanonicalIdentityValuesThatBecomeEmpty() {
        assertThrows(IllegalArgumentException.class,
                () -> ResidentIdentityKeyNormalizer.buildingKey(" \t\u00a0 "));
        assertThrows(IllegalArgumentException.class,
                () -> ResidentIdentityKeyNormalizer.apartmentCodeKey("   "));
        assertThrows(IllegalArgumentException.class,
                () -> ResidentIdentityKeyNormalizer.identityNumberKey(" \u2003 "));
    }
}
