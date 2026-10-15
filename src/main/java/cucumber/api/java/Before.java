package cucumber.api.java;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Legacy Cucumber API compatibility annotation.
 * Newer code uses io.cucumber.java.Before
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Before {
    /** Tag expressions; the hook only runs for scenarios matching all of them. */
    String[] value() default {};
    long timeout() default 0L;
    /** Lower order runs first. */
    int order() default 10000;
}
