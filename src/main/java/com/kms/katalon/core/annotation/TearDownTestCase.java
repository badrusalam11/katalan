package com.kms.katalon.core.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Katalon-compatible {@code @TearDownTestCase} annotation. Alias for
 * {@link AfterTestCase}, kept for compatibility with Katalon listeners.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface TearDownTestCase {
}
