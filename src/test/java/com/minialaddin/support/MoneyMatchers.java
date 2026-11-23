
package com.minialaddin.support;

import org.hamcrest.BaseMatcher;
import org.hamcrest.Description;
import org.hamcrest.Matcher;

import java.math.BigDecimal;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Assertions for monetary values in JSON responses.
 *
 * JsonPath parses JSON numbers as doubles, so comparing them with a
 * double literal relies on floating-point equality and cannot see how
 * many decimal places the API actually returned. {@link #money(String)}
 * compares the parsed value as a BigDecimal, and
 * {@link #assertMoneyField(String, String, String)} checks the raw JSON
 * text, which is where the two-decimal display contract (R3) is visible.
 */
public final class MoneyMatchers {

    private MoneyMatchers() {
    }

    /** Matches a JSON number equal to {@code expected} as a decimal value. */
    public static Matcher<Object> money(String expected) {
        BigDecimal expectedAmount = new BigDecimal(expected);

        return new BaseMatcher<>() {

            @Override
            public boolean matches(Object actual) {
                return actual instanceof Number number
                        && new BigDecimal(number.toString())
                        .compareTo(expectedAmount) == 0;
            }

            @Override
            public void describeTo(Description description) {
                description.appendText("a monetary amount equal to ")
                        .appendValue(expectedAmount);
            }
        };
    }

    /**
     * Asserts that {@code field} appears in the raw JSON with exactly the
     * textual value {@code expected}, for example {@code 7160.00}.
     */
    public static void assertMoneyField(
            String json,
            String field,
            String expected
    ) {
        Pattern pattern = Pattern.compile(
                "\"" + Pattern.quote(field) + "\"\\s*:\\s*"
                        + Pattern.quote(expected) + "(?![0-9])"
        );

        assertTrue(
                pattern.matcher(json).find(),
                () -> "Expected \"" + field + "\" serialised as "
                        + expected + " in " + json
        );
    }
}
