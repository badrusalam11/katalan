package com.kms.katalon.core.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Katalon-compatible {@code @TearDown} annotation. Alias for
 * {@link AfterTestSuite} kept for backward compatibility with existing
 * Katalon listener scripts.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface TearDown {
}
