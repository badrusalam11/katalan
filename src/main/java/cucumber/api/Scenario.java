package cucumber.api;

import java.util.Collection;
import java.util.List;

/**
 * Legacy Cucumber API compatibility interface (Cucumber-JVM 4.x shape),
 * passed to {@code @cucumber.api.java.Before} / {@code @After} hooks.
 */
public interface Scenario {
    Collection<String> getSourceTagNames();

    /** "passed", "failed", "skipped" or "undefined". */
    String getStatus();

    boolean isFailed();

    void embed(byte[] data, String mimeType);

    void embed(byte[] data, String mimeType, String name);

    void write(String text);

    String getName();

    /** "feature-name;scenario-name", as Cucumber-JVM 4.x builds it. */
    String getId();

    String getUri();

    List<Integer> getLines();
}
