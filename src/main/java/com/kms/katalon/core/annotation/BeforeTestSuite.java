package com.kms.katalon.core.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Katalon-compatible {@code @BeforeTestSuite} annotation.
 * <p>Marks a method to be executed <b>before a Test Suite</b> starts.
 * The annotated method may optionally accept a
 * {@link com.kms.katalon.core.context.TestSuiteContext} parameter.</p>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface BeforeTestSuite {
}
