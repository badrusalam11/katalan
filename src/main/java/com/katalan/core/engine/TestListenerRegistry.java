package com.katalan.core.engine;

import com.katalan.core.compat.GroovySourcePreprocessor;
import com.katalan.core.logging.GroovySourceParser;
import com.kms.katalon.core.annotation.AfterTestCase;
import com.kms.katalon.core.annotation.AfterTestSuite;
import com.kms.katalon.core.annotation.BeforeTestCase;
import com.kms.katalon.core.annotation.BeforeTestSuite;
import com.kms.katalon.core.annotation.SetUp;
import com.kms.katalon.core.annotation.SetupTestCase;
import com.kms.katalon.core.annotation.TearDown;
import com.kms.katalon.core.annotation.TearDownTestCase;
import com.kms.katalon.core.context.TestCaseContext;
import com.kms.katalon.core.context.TestSuiteContext;
import groovy.lang.GroovyClassLoader;
import org.codehaus.groovy.control.CompilerConfiguration;
import org.codehaus.groovy.control.customizers.ImportCustomizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Registry of Test Listener instances.
 *
 * <p>Loads every Groovy class found in the project's
 * {@code Test Listeners/} folder, instantiates it once and dispatches the
 * appropriate Katalon-compatible lifecycle annotations
 * ({@link BeforeTestSuite}, {@link AfterTestSuite}, {@link BeforeTestCase},
 * {@link AfterTestCase}, {@link SetUp}, {@link TearDown},
 * {@link SetupTestCase}, {@link TearDownTestCase}) at the matching
 * lifecycle points.</p>
 *
 * <p>Listener methods may declare zero arguments or a single
 * {@link TestCaseContext} / {@link TestSuiteContext} argument. Failures
 * inside listeners are logged but do not abort the test execution.</p>
 */
public class TestListenerRegistry {

    private static final Logger logger = LoggerFactory.getLogger(TestListenerRegistry.class);

    /** A loaded listener plus the classloader used to compile it. */
    private static final class Entry {
        final Object instance;
        final ClassLoader loader;
        Entry(Object instance, ClassLoader loader) {
            this.instance = instance;
            this.loader = loader;
        }
    }

    private final List<Entry> listenerInstances = new ArrayList<>();

    /**
     * Scan the project's {@code Test Listeners/} folder and build the listener registry.
     *
     * <p>Both the project's {@code Keywords/} folder and the {@code Test Listeners/}
     * folder are preprocessed via {@link GroovySourcePreprocessor} into temp
     * directories before compilation, so that Katalon-era Groovy sources
     * (which rely on Groovy 2.x/3.x behaviour and Katalon IDE magic imports)
     * can be compiled by Groovy 4.</p>
     *
     * @param projectPath the Katalon project path
     * @param parentLoader the parent classloader of the existing Groovy shell
     *                     (needed so listeners can see katalan / Katalon compat classes)
     */
    public void loadListeners(Path projectPath, GroovyClassLoader parentLoader) {
        listenerInstances.clear();

        if (projectPath == null) {
            return;
        }

        Path listenersDir = projectPath.resolve("Test Listeners");
        if (!Files.isDirectory(listenersDir)) {
            logger.debug("No 'Test Listeners' folder at {} - skipping listener loading", listenersDir);
            return;
        }

        // Use a NEUTRAL parent classloader (katalan-runner's own) instead of the
        // GroovyShell's classloader, because the shell has the original
        // project Keywords/ path on its classpath. If we inherit that, Groovy
        // will resolve references like `support.JavascriptExecution` to the
        // raw (un-preprocessed) source and fail with `unable to resolve class
        // driver` / `XmlSlurper` even though we've already produced a
        // preprocessed copy. We only need the parent to provide JDK classes,
        // katalan-runner classes (kms.katalon.* stubs, com.katalan.*) and
        // any JARs in the JVM classpath - all of which live on the class
        // loader that loaded this class.
        ClassLoader isolatedParent = TestListenerRegistry.class.getClassLoader();

        // Build a dedicated GroovyClassLoader for listeners with its own compiler
        // configuration (extra default imports) so we don't pollute the main
        // script execution classloader.
        CompilerConfiguration cc = new CompilerConfiguration();
        ImportCustomizer imports = new ImportCustomizer();
        imports.addStarImports(
                "com.kms.katalon.core.annotation",
                "com.kms.katalon.core.context",
                "com.kms.katalon.core.logging",
                "groovy.xml"
        );
        cc.addCompilationCustomizers(imports);

        // Preprocess Keywords/ so that when the listener transitively resolves
        // keyword classes, the Groovy 4-compatible sources are used instead of
        // the raw Katalon-era ones (which break on things like XmlSlurper or
        // `((driver) as JavascriptExecutor)`).
        String keywordsClasspath = null;
        Path keywordsDir = projectPath.resolve("Keywords");
        if (Files.isDirectory(keywordsDir)) {
            try {
                keywordsClasspath = GroovySourcePreprocessor
                        .createPreprocessedCopy(keywordsDir, "listener-keywords").toString();
            } catch (Exception e) {
                logger.warn("Could not preprocess Keywords for listeners: {}", e.getMessage());
                keywordsClasspath = keywordsDir.toString();
            }
        }

        // Preprocess Include/scripts/groovy (if present) - some listeners import from here
        String includeClasspath = null;
        Path includeGroovy = projectPath.resolve("Include").resolve("scripts").resolve("groovy");
        if (Files.isDirectory(includeGroovy)) {
            try {
                includeClasspath = GroovySourcePreprocessor
                        .createPreprocessedCopy(includeGroovy, "listener-include").toString();
            } catch (Exception e) {
                logger.warn("Could not preprocess Include/scripts/groovy for listeners: {}", e.getMessage());
            }
        }

        // Preprocess the listener sources themselves
        Path listenersWorkDir;
        try {
            listenersWorkDir = GroovySourcePreprocessor.createPreprocessedCopy(listenersDir, "listeners");
        } catch (Exception e) {
            logger.warn("Could not preprocess Test Listeners folder: {}", e.getMessage());
            listenersWorkDir = listenersDir;
        }

        List<Path> groovyFiles = new ArrayList<>();
        try {
            Files.walk(listenersWorkDir)
                    .filter(p -> p.toString().toLowerCase().endsWith(".groovy"))
                    .forEach(groovyFiles::add);
        } catch (Exception e) {
            logger.warn("Could not scan 'Test Listeners' folder: {}", e.getMessage());
            return;
        }

        // Stable load order
        groovyFiles.sort(Comparator.comparing(Path::toString));

        for (Path file : groovyFiles) {
            // Fresh classloader per listener so a transitive compilation
            // failure in one listener does not poison the compilation of
            // the next one (Groovy caches failures per-classloader and
            // re-raises them as a 'BUG! exception ... There should not have
            // been any compilation from this call').
            GroovyClassLoader listenerLoader = new GroovyClassLoader(isolatedParent, cc);
            if (keywordsClasspath != null) listenerLoader.addClasspath(keywordsClasspath);
            if (includeClasspath != null)  listenerLoader.addClasspath(includeClasspath);
            listenerLoader.addClasspath(listenersWorkDir.toString());

            // Add project's JAR libraries so custom keywords referenced
            // transitively by listeners can resolve their types.
            addJarsToLoader(listenerLoader, projectPath.resolve("Libs"));
            addJarsToLoader(listenerLoader, projectPath.resolve("Drivers"));
            addJarsToLoader(listenerLoader, projectPath.resolve("bin").resolve("lib"));

            try {
                Class<?> clazz = listenerLoader.parseClass(file.toFile());
                Object instance = clazz.getDeclaredConstructor().newInstance();
                
                // Parse listener source to extract statement structure for detailed logging
                try {
                    Path originalFile = listenersDir.resolve(listenersWorkDir.relativize(file));
                    if (Files.exists(originalFile)) {
                        GroovySourceParser.parseListenerSource(
                                originalFile.toString(),
                                clazz.getName()
                        );
                    }
                } catch (Exception parseEx) {
                    logger.debug("Could not parse listener source for {}: {}", 
                            clazz.getName(), parseEx.getMessage());
                }
                
                listenerInstances.add(new Entry(instance, listenerLoader));
                logger.info("Loaded Test Listener: {}", clazz.getName());
            } catch (Throwable t) {
                // Extract root cause message; full trace at debug level only.
                Throwable root = t;
                while (root.getCause() != null && root.getCause() != root) root = root.getCause();
                logger.warn("Skipped Test Listener {} ({}):\n{}",
                        file.getFileName(),
                        root.getClass().getSimpleName(),
                        summarise(root.getMessage()));
                logger.debug("Listener load stacktrace for {}", file.getFileName(), t);
                // Best-effort: release any cached class references
                try { listenerLoader.clearCache(); } catch (Throwable ignored) {}
            }
        }

        logger.info("Test Listener registry initialised with {} listener(s)", listenerInstances.size());
    }

    /**
     * Trim a (potentially huge) compiler error message to something useful in
     * a single log line block: the first ~6 non-empty lines.
     */
    private static String summarise(String s) {
        if (s == null || s.isEmpty()) return "(no message)";
        String[] lines = s.split("\\R");
        StringBuilder out = new StringBuilder();
        int kept = 0;
        for (String line : lines) {
            if (line.trim().isEmpty()) continue;
            out.append("    ").append(line).append('\n');
            if (++kept >= 8) { out.append("    ..."); break; }
        }
        return out.toString().stripTrailing();
    }

    /**
     * Add every {@code .jar} found (non-recursively) inside {@code dir} to the
     * given GroovyClassLoader. Silently ignores non-existent directories.
     */
    private static void addJarsToLoader(GroovyClassLoader loader, Path dir) {
        if (dir == null || !Files.isDirectory(dir)) return;
        try {
            Files.walk(dir, 1)
                    .filter(p -> p.toString().toLowerCase().endsWith(".jar"))
                    .forEach(jar -> {
                        try {
                            loader.addURL(jar.toUri().toURL());
                        } catch (Exception ignored) { /* best-effort */ }
                    });
        } catch (Exception ignored) { /* best-effort */ }
    }

    /** Get loaded listener instances (read-only). */
    public List<Object> getListeners() {
        List<Object> out = new ArrayList<>(listenerInstances.size());
        for (Entry e : listenerInstances) out.add(e.instance);
        return Collections.unmodifiableList(out);
    }

    /**
     * Return the unique set of classloaders used to compile/load the listeners.
     * These are the classloaders that loaded cs-library-fat and other Libs/ JARs,
     * so any static field injection (e.g. CSReport.reportFolder) must also be done
     * on the class instances visible from these loaders — not just from the
     * thread-context or system classloader.
     */
    public List<ClassLoader> getListenerClassLoaders() {
        List<ClassLoader> out = new ArrayList<>();
        for (Entry e : listenerInstances) {
            if (e.loader != null && !out.contains(e.loader)) {
                out.add(e.loader);
            }
        }
        return Collections.unmodifiableList(out);
    }

    public boolean isEmpty() {
        return listenerInstances.isEmpty();
    }

    // ----- Test Suite hooks --------------------------------------------------

    public void invokeBeforeTestSuite(TestSuiteContext ctx) {
        invoke(BeforeTestSuite.class, ctx);
        // Katalon legacy alias
        invoke(SetUp.class, ctx);
    }

    public void invokeAfterTestSuite(TestSuiteContext ctx) {
        invoke(AfterTestSuite.class, ctx);
        invoke(TearDown.class, ctx);
    }

    // ----- Test Case hooks ---------------------------------------------------

    public void invokeBeforeTestCase(TestCaseContext ctx) {
        invoke(BeforeTestCase.class, ctx);
        invoke(SetupTestCase.class, ctx);
    }

    public void invokeAfterTestCase(TestCaseContext ctx) {
        invoke(AfterTestCase.class, ctx);
        invoke(TearDownTestCase.class, ctx);
    }

    // ----- Internal ----------------------------------------------------------

    private void invoke(Class<? extends Annotation> annotationType, Object contextArg) {
        if (listenerInstances.isEmpty()) {
            return;
        }

        for (Entry entry : listenerInstances) {
            Object listener = entry.instance;
            for (Method method : listener.getClass().getDeclaredMethods()) {
                if (!method.isAnnotationPresent(annotationType)) {
                    continue;
                }
                try {
                    if (!method.canAccess(listener)) {
                        method.setAccessible(true);
                    }
                } catch (Exception ignored) {
                    method.setAccessible(true);
                }

                // Temporarily set the Thread context classloader to the
                // listener's loader. This is required because many Katalon
                // listeners spin up their own `new GroovyShell()` internally
                // (e.g. CSReport.exportKatalonReports) which relies on the
                // context CL to resolve project-local JARs (Drivers/, Libs/).
                Thread currentThread = Thread.currentThread();
                ClassLoader previousCL = currentThread.getContextClassLoader();
                try {
                    currentThread.setContextClassLoader(entry.loader);
                    
                    // Get stored statements for this listener method
                    String className = listener.getClass().getName();
                    String methodName = method.getName();
                    java.util.List<com.katalan.core.logging.GroovySourceParser.StatementInfo> statements =
                        com.katalan.core.logging.GroovySourceParser.getListenerMethodStatements(className, methodName);
                    
                    com.katalan.core.logging.XmlKeywordLogger kwLogger = 
                        com.katalan.core.logging.XmlKeywordLogger.getInstance();
                    
                    // Log listener method start
                    String listenerAction = "listener action : " + methodName;
                    kwLogger.startKeyword(listenerAction, null, null);
                    
                    // If we have statement-level details, log them BEFORE invoke
                    if (statements != null && !statements.isEmpty()) {
                        int stepIndex = 1;
                        for (com.katalan.core.logging.GroovySourceParser.StatementInfo stmt : statements) {
                            kwLogger.startKeyword(stmt.actionText, stmt.lineNumber, stepIndex++);
                            kwLogger.endKeyword(stmt.actionText);
                        }
                    }

                    int paramCount = method.getParameterCount();
                    if (paramCount == 0) {
                        method.invoke(listener);
                    } else if (paramCount == 1) {
                        Class<?> paramType = method.getParameterTypes()[0];
                        if (contextArg != null && paramType.isInstance(contextArg)) {
                            method.invoke(listener, contextArg);
                        } else {
                            // Pass null if types don't line up rather than failing hard
                            method.invoke(listener, new Object[]{null});
                        }
                    } else {
                        logger.warn("Listener method {}#{} has unsupported arity {} - skipping",
                                listener.getClass().getName(), method.getName(), paramCount);
                    }
                    
                    // Log listener method end
                    kwLogger.endKeyword(listenerAction);
                    
                    logger.debug("Invoked @{} on {}#{}",
                            annotationType.getSimpleName(),
                            listener.getClass().getName(), method.getName());
                } catch (Throwable t) {
                    Throwable cause = t.getCause() != null ? t.getCause() : t;
                    logger.error("Test Listener {}#{} (@{}) threw: {}",
                            listener.getClass().getName(), method.getName(),
                            annotationType.getSimpleName(), cause.getMessage(), cause);
                } finally {
                    currentThread.setContextClassLoader(previousCL);
                }
            }
        }
    }
}
