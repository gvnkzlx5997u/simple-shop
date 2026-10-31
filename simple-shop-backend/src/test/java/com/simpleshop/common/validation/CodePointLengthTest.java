package com.simpleshop.common.validation;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.Validation;
import jakarta.validation.Validator;

import org.junit.jupiter.api.Test;

class CodePointLengthTest {

    private static final Validator VALIDATOR =
            Validation.buildDefaultValidatorFactory().getValidator();

    record Sample(@CodePointLength(max = 50) String value) {
    }

    @Test
    void countsCodePointsRatherThanUtf16Units() {
        assertThat(VALIDATOR.validate(new Sample("😀".repeat(50)))).isEmpty();
        assertThat(VALIDATOR.validate(new Sample("😀".repeat(51)))).hasSize(1);
        assertThat(VALIDATOR.validate(new Sample("a".repeat(50)))).isEmpty();
    }

    @Test
    void supportsMinimumAndLeavesNullToPresenceConstraints() {
        record Bounded(@CodePointLength(min = 2, max = 4) String value) {
        }

        assertThat(VALIDATOR.validate(new Bounded(null))).isEmpty();
        assertThat(VALIDATOR.validate(new Bounded("a"))).hasSize(1);
        assertThat(VALIDATOR.validate(new Bounded("abcd"))).isEmpty();
    }
}
