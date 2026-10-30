package com.simpleshop.common.validation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

/**
 * 按 Unicode 码点计数的长度约束，与 {@code TextRules.charCount} 及数据库字符数口径一致。
 *
 * <p>标准 {@code @Size} 按 UTF-16 单元计数，一个 emoji 会算两个；本注解避免把
 * 50 个 emoji 的合法文本误判为超长。
 */
@Documented
@Constraint(validatedBy = CodePointLengthValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
public @interface CodePointLength {

    int min() default 0;

    int max() default Integer.MAX_VALUE;

    String message() default "must be at most {max} characters";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
