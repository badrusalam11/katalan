package cucumber.api.java.en;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Legacy Cucumber API compatibility annotation.
 * Newer code uses io.cucumber.java.en.Given
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Given {
    String value();
    long timeout() default 0L;
}
