package com.simpleshop.common.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/** {@link CodePointLength} 的实现；{@code null} 交给 {@code @NotNull/@NotBlank} 处理。 */
public class CodePointLengthValidator implements ConstraintValidator<CodePointLength, CharSequence> {

    private int min;
    private int max;

    @Override
    public void initialize(CodePointLength annotation) {
        this.min = annotation.min();
        this.max = annotation.max();
    }

    @Override
    public boolean isValid(CharSequence value, ConstraintValidatorContext context) {
        if (value == null) {
            return true;
        }
        int count = Character.codePointCount(value, 0, value.length());
        return count >= min && count <= max;
    }
}
