package com.katalan.core.compat;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Utility to preprocess Groovy source files written for Katalon (Groovy 2.x/3.x)
 * so they can be compiled by Groovy 4.x which is stricter.
 *
 * Known issues handled:
 *  1. `((identifier) as Type)` - parsed by Groovy 4 as Java-style cast "(Type)expr".
 *     Rewritten to `(identifier as Type)`.
 *  2. `import internal.GlobalVariable as GlobalVariable` left alone; the stub class exists.
 */
public class GroovySourcePreprocessor {

    private static final Logger logger = LoggerFactory.getLogger(GroovySourcePreprocessor.class);

    // Matches: (( identifier ) as Type)  - captures the identifier and the type
    // Only simple identifiers (no dots/method calls) to be safe
    private static final Pattern DOUBLE_PAREN_CAST =
            Pattern.compile("\\(\\(\\s*([A-Za-z_][A-Za-z0-9_]*)\\s*\\)\\s+as\\s+([A-Za-z_][A-Za-z0-9_.]*)\\s*\\)");

    /**
     * Apply all known transformations to a source string.
     */
    public static String preprocess(String source) {
        if (source == null || source.isEmpty()) return source;

        String result = source;

        // Fix: ((x) as Type) -> (x as Type)
        Matcher m = DOUBLE_PAREN_CAST.matcher(result);
        if (m.find()) {
            result = m.replaceAll("($1 as $2)");
        }

        // Comment out unsupported Katalon imports so Groovy compiler doesn't fail.
        // We only comment out packages not stubbed in our compat layer.

        // mobile.keyword.builtin.* - not stubbed (specific builtin keyword classes)
        result = result.replaceAll(
            "(?m)^(\\s*)import\\s+com\\.kms\\.katalon\\.core\\.mobile\\.keyword\\.builtin\\.([A-Za-z0-9_.]+)",
            "$1// import com.kms.katalon.core.mobile.keyword.builtin.$2 - not supported"
        );

        // javax.servlet.* - not bundled, rarely needed
        result = result.replaceAll(
            "(?m)^(\\s*)import\\s+javax\\.servlet\\.([A-Za-z0-9_.]+)",
            "$1// import javax.servlet.$2 - not bundled"
        );

        // Appium - not bundled (mobile)
        result = result.replaceAll(
            "(?m)^(\\s*)import\\s+io\\.appium\\.([A-Za-z0-9_.]+)",
            "$1// import io.appium.$2 - not bundled"
        );

        // Eclipse OSGi - not bundled
        result = result.replaceAll(
            "(?m)^(\\s*)import\\s+org\\.eclipse\\.([A-Za-z0-9_.]+)",
            "$1// import org.eclipse.$2 - not bundled"
        );

        // groovy.inspect.swingui - GUI tools not in core groovy
        result = result.replaceAll(
            "(?m)^(\\s*)import\\s+groovy\\.inspect\\.swingui\\.([A-Za-z0-9_.]+)",
            "$1// import groovy.inspect.swingui.$2 - not bundled"
        );

        // Old apache commons lang (use lang3 instead) - often not available
        result = result.replaceAll(
            "(?m)^(\\s*)import\\s+org\\.apache\\.commons\\.lang\\.([A-Z][A-Za-z0-9_.]*)",
            "$1// import org.apache.commons.lang.$2 - not available"
        );

        // ----------------------------------------------------------------------
        // NOTE: We intentionally DO NOT rewrite com.kms.katalon.core.* imports
        // to katalan equivalents here. The kms.* compatibility stubs bundled
        // in katalan-runner already delegate to com.katalan.keywords.WebUI etc.
        // Rewriting breaks class inheritance (e.g. CSWeb extends WebUI loses
        // disableSmartWait / openBrowser(url, FailureHandling) overloads that
        // only exist on the kms stub, not on com.katalan.keywords.WebUI).
        // The only thing we must rewrite is `internal.GlobalVariable` because
        // there is no `internal` package - it is a Katalon IDE-generated path.
        // ----------------------------------------------------------------------

        // internal.GlobalVariable -> katalan compat GlobalVariable (preserve alias)
        result = result.replaceAll(
            "import\\s+internal\\.GlobalVariable(\\s+as\\s+\\w+)?",
            "import com.katalan.core.compat.GlobalVariable$1"
        );

        // Old Cucumber API -> io.cucumber (Cucumber 7.x)
        result = result.replaceAll(
            "import\\s+cucumber\\.api\\.java\\.en\\.(Given|When|Then|And|But)",
            "import io.cucumber.java.en.$1"
        );

        // Groovy 4: XmlSlurper / XmlParser / XmlNodePrinter moved to groovy.xml
        // and are no longer default imports. Inject explicit imports if the
        // source references them without importing groovy.xml.*.
        boolean needsXmlImports =
                (result.contains("XmlSlurper") || result.contains("XmlParser") || result.contains("XmlNodePrinter"))
                && !result.contains("import groovy.xml.");
        if (needsXmlImports) {
            StringBuilder xmlImports = new StringBuilder();
            if (result.contains("XmlSlurper"))     xmlImports.append("import groovy.xml.XmlSlurper\n");
            if (result.contains("XmlParser"))      xmlImports.append("import groovy.xml.XmlParser\n");
            if (result.contains("XmlNodePrinter")) xmlImports.append("import groovy.xml.XmlNodePrinter\n");
            result = injectAfterPackage(result, xmlImports.toString());
        }

        return result;
    }

    /**
     * Insert the given block right after the {@code package ...} line if any,
     * otherwise at the very top of the file.
     */
    private static String injectAfterPackage(String source, String block) {
        Matcher pkg = Pattern.compile("(?m)^\\s*package\\s+[A-Za-z0-9_.]+\\s*;?\\s*$").matcher(source);
        if (pkg.find()) {
            int insertAt = pkg.end();
            return source.substring(0, insertAt) + "\n" + block + source.substring(insertAt);
        }
        return block + source;
    }

    /**
     * Recursively copy and preprocess all .groovy files from srcDir to destDir.
     * Non-groovy files are copied as-is.
     */
    public static void preprocessDirectory(Path srcDir, Path destDir) throws IOException {
        if (!Files.exists(srcDir)) return;
        Files.createDirectories(destDir);

        try (Stream<Path> stream = Files.walk(srcDir)) {
            stream.forEach(src -> {
                try {
                    Path rel = srcDir.relativize(src);
                    Path dest = destDir.resolve(rel.toString());
                    if (Files.isDirectory(src)) {
                        Files.createDirectories(dest);
                        return;
                    }
                    Files.createDirectories(dest.getParent());
                    if (src.toString().toLowerCase().endsWith(".groovy")) {
                        String content = new String(Files.readAllBytes(src), StandardCharsets.UTF_8);
                        String fixed = preprocess(content);
                        Files.write(dest, fixed.getBytes(StandardCharsets.UTF_8));
                    } else {
                        Files.copy(src, dest, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    }
                } catch (IOException e) {
                    logger.warn("Failed to preprocess {}: {}", src, e.getMessage());
                }
            });
        }

        logger.info("Preprocessed groovy sources: {} -> {}", srcDir, destDir);
    }

    /**
     * Create a temp directory and preprocess the given source directory into it.
     * Returns the temp directory path.
     */
    public static Path createPreprocessedCopy(Path srcDir, String tagName) throws IOException {
        Path tmp = Files.createTempDirectory("katalan-" + tagName + "-");
        tmp.toFile().deleteOnExit();
        preprocessDirectory(srcDir, tmp);
        return tmp;
    }
}
