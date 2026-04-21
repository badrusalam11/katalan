package com.kms.katalon.core.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Katalon-compatible {@code @BeforeTestCase} annotation.
 * <p>Marks a method (defined in a Groovy class inside the project's
 * {@code Test Listeners/} folder) to be executed by the Katalan engine
 * <b>before every Test Case</b> runs.</p>
 *
 * <p>The annotated method may optionally accept a single
 * {@link com.kms.katalon.core.context.TestCaseContext} argument which
 * will be populated by the engine.</p>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface BeforeTestCase {
}
