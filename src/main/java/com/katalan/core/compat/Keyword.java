package com.katalan.core.compat;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Keyword annotation - marks a method as a custom keyword
 * Compatible with Katalon's @Keyword annotation
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Keyword {
    /**
     * The name of the keyword
     */
    String keywordObject() default "";
}
