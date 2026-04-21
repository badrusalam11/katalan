package com.kms.katalon.core.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Katalon-compatible {@code @AfterTestCase} annotation.
 * <p>Marks a method to be executed by Katalan <b>after every Test Case</b>
 * has finished (regardless of its status). The annotated method may
 * optionally accept a {@link com.kms.katalon.core.context.TestCaseContext}
 * parameter.</p>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface AfterTestCase {
}
