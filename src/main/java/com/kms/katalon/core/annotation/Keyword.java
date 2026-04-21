package com.kms.katalon.core.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Katalon compatibility annotation for @Keyword
 * Marks a method as a custom keyword that can be called via CustomKeywords.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
public @interface Keyword {
    String keyword() default "";
    String[] keywordObject() default {};
}
