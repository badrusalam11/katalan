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
import java.net.URL;
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
    
    /** Last injected reportFolder path, used for per-invocation re-injection */
    private String lastInjectedReportFolder = null;

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

        // ---------------------------------------------------------------
        // Collect project JAR URLs ONCE here, then reuse for every listener
        // loader.  The original code called addJarsToLoader() inside the
        // per-listener loop which caused 3 × N directory scans for N listeners.
        // Collecting once reduces that to exactly 3 scans regardless of N.
        // NOTE: Both Libs/, Drivers/, and bin/lib/ are still scanned; the
        // compatibility guarantee (listeners can see all project JARs) is
        // fully preserved — only the discovery work is deduplicated.
        // ---------------------------------------------------------------
        List<URL> projectJarUrls = collectProjectJarUrls(projectPath);
        logger.debug("[startup] listener-loader: collected {} project JAR URL(s) for {} listener file(s)",
                projectJarUrls.size(), groovyFiles.size());

        long listenerLoadStart = System.currentTimeMillis();
        long slowestMs = 0;
        String slowestName = null;
        int failCount = 0;

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

            // Add project's JAR libraries — use pre-collected URLs (no re-scan).
            for (URL url : projectJarUrls) {
                listenerLoader.addURL(url);
            }

            long t0 = System.currentTimeMillis();
            try {
                Class<?> clazz = listenerLoader.parseClass(file.toFile());
                Object instance = clazz.getDeclaredConstructor().newInstance();
                long td = System.currentTimeMillis() - t0;

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
                logger.info("Loaded Test Listener: {} ({} ms)", clazz.getName(), td);
                logger.debug("[startup] listener '{}' loaded in {} ms", clazz.getName(), td);

                if (td > slowestMs) { slowestMs = td; slowestName = clazz.getName(); }
            } catch (Throwable t) {
                long td = System.currentTimeMillis() - t0;
                failCount++;
                // Extract root cause message; full trace at debug level only.
                Throwable root = t;
                while (root.getCause() != null && root.getCause() != root) root = root.getCause();
                logger.warn("Skipped Test Listener {} ({}):\n{}",
                        file.getFileName(),
                        root.getClass().getSimpleName(),
                        summarise(root.getMessage()));
                logger.debug("[startup] listener '{}' FAILED in {} ms", file.getFileName(), td, t);
                // Best-effort: release any cached class references
                try { listenerLoader.clearCache(); } catch (Throwable ignored) {}
            }
        }

        long totalListenerMs = System.currentTimeMillis() - listenerLoadStart;
        logger.info("[startup] Test Listeners: loaded={}, failed={}, total_time={} ms",
                listenerInstances.size(), failCount, totalListenerMs);
        if (slowestName != null) {
            logger.info("[startup] Slowest listener: {} ({} ms)", slowestName, slowestMs);
        }
        logger.info("Test Listener registry initialised with {} listener(s)", listenerInstances.size());
    }

    /**
     * Collect all project JAR {@link URL}s from {@code Libs/}, {@code Drivers/},
     * and {@code bin/lib/} in one pass.  Used to pre-populate each per-listener
     * {@link GroovyClassLoader} without re-scanning the directories N times.
     *
     * @param projectPath root of the Katalon project
     * @return unmodifiable list of JAR URLs (never {@code null})
     */
    private static List<URL> collectProjectJarUrls(Path projectPath) {
        List<URL> urls = new ArrayList<>();
        for (String dir : new String[]{"Libs", "Drivers", "bin/lib"}) {
            Path d = projectPath.resolve(dir);
            if (!Files.isDirectory(d)) continue;
            try {
                Files.walk(d, 1)
                        .filter(p -> p.toString().toLowerCase().endsWith(".jar"))
                        .forEach(jar -> {
                            try {
                                urls.add(jar.toUri().toURL());
                                logger.debug("[startup] listener-jar: {}", jar.getFileName());
                            } catch (Exception ignored) { /* best-effort */ }
                        });
            } catch (Exception ignored) { /* best-effort */ }
        }
        return Collections.unmodifiableList(urls);
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

    /**
     * Inject reportFolder into denstoo.reporting.CSReport static fields
     * across all listener classloaders. This ensures that when @AfterTestSuite
     * listeners call CSReport.exportKatalonReports(), the reportFolder is available.
     * 
     * @param reportFolderPath the absolute path to the report folder
     */
    public void injectReportFolderIntoListeners(String reportFolderPath) {
        if (reportFolderPath == null || reportFolderPath.isEmpty()) {
            logger.warn("Cannot inject null or empty reportFolder into listeners");
            return;
        }
        
        // Store for per-invocation re-injection
        this.lastInjectedReportFolder = reportFolderPath;
        
        // Inject into each listener's classloader
        for (Entry entry : listenerInstances) {
            if (entry.loader == null) continue;
            
            try {
                Class<?> csReportClass = entry.loader.loadClass("denstoo.reporting.CSReport");
                
                // Inject reportFolder
                try {
                    java.lang.reflect.Field reportFolderField = csReportClass.getDeclaredField("reportFolder");
                    reportFolderField.setAccessible(true);
                    reportFolderField.set(null, reportFolderPath);
                    logger.info("✅ Injected reportFolder into listener CSReport ({}): {}", 
                            entry.instance.getClass().getSimpleName(), reportFolderPath);
                } catch (NoSuchFieldException e) {
                    logger.debug("CSReport.reportFolder field not found in listener classloader");
                }
                
                // Inject minimal report list if field exists
                try {
                    java.lang.reflect.Field reportField = csReportClass.getDeclaredField("report");
                    reportField.setAccessible(true);
                    Object currentValue = reportField.get(null);
                    if (currentValue == null) {
                        reportField.set(null, new java.util.ArrayList<>());
                        logger.debug("Injected empty report list into listener CSReport");
                    }
                } catch (NoSuchFieldException e) {
                    logger.debug("CSReport.report field not found in listener classloader");
                }
                
            } catch (ClassNotFoundException e) {
                // CSReport not used by this listener, skip
                logger.debug("CSReport class not found in listener {} classloader", 
                        entry.instance.getClass().getSimpleName());
            } catch (Exception e) {
                logger.warn("Failed to inject reportFolder into listener {}: {}", 
                        entry.instance.getClass().getSimpleName(), e.getMessage());
            }
        }
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
                    String listenerAction = methodName;
                    kwLogger.startListener(listenerAction);
                    
                    // If we have statement-level details, log them BEFORE invoke
                    if (statements != null && !statements.isEmpty()) {
                        int stepIndex = 1;
                        for (com.katalan.core.logging.GroovySourceParser.StatementInfo stmt : statements) {
                            kwLogger.startKeyword(stmt.actionText, stmt.lineNumber, stepIndex++);
                            kwLogger.endKeyword(stmt.actionText);
                        }
                    }

                    // Inject reportFolder into ALL classloaders RIGHT BEFORE invocation
                    // This is critical because some listeners (like CSReport) create nested GroovyShells
                    // that load CSReport in completely new classloader contexts
                    if (lastInjectedReportFolder != null) {
                        // CRITICAL: Set as system property so nested GroovyShells can access it
                        System.setProperty("katalan.reportFolder", lastInjectedReportFolder);
                        
                        // Inject into EVERY classloader we can find
                        java.util.LinkedHashSet<ClassLoader> classloaders = new java.util.LinkedHashSet<>();
                        classloaders.add(entry.loader); // This listener's classloader
                        classloaders.add(Thread.currentThread().getContextClassLoader());
                        classloaders.add(ClassLoader.getSystemClassLoader());
                        classloaders.add(getClass().getClassLoader());
                        classloaders.remove(null);
                        
                        for (ClassLoader cl : classloaders) {
                            try {
                                Class<?> csReportClass = cl.loadClass("denstoo.reporting.CSReport");
                                
                                // Inject reportFolder field
                                try {
                                    java.lang.reflect.Field reportFolderField = csReportClass.getDeclaredField("reportFolder");
                                    reportFolderField.setAccessible(true);
                                    reportFolderField.set(null, lastInjectedReportFolder);
                                } catch (NoSuchFieldException ignored) {}
                                
                                // Inject report list with reportFolder inside
                                try {
                                    java.lang.reflect.Field reportField = csReportClass.getDeclaredField("report");
                                    reportField.setAccessible(true);
                                    
                                    // Create a list with one map containing reportFolder
                                    java.util.List<java.util.Map<String,Object>> reportList = new java.util.ArrayList<>();
                                    java.util.Map<String,Object> reportMap = new java.util.HashMap<>();
                                    reportMap.put("reportFolder", lastInjectedReportFolder);
                                    reportMap.put("testCaseName", "dummy");
                                    reportMap.put("testCaseId", "dummy");
                                    reportList.add(reportMap);
                                    
                                    reportField.set(null, reportList);
                                    logger.debug("Injected report list into CSReport via {}", cl.getClass().getSimpleName());
                                } catch (NoSuchFieldException ignored) {}
                            } catch (ClassNotFoundException ignored) {
                                // CSReport not loaded in this classloader yet
                            } catch (Exception e) {
                                logger.debug("Failed to inject into classloader {}: {}", 
                                        cl.getClass().getSimpleName(), e.getMessage());
                            }
                        }
                    }

                    logger.info("🔔 Invoking {}@{} on listener: {}", 
                            annotationType.getSimpleName(), methodName, listener.getClass().getSimpleName());

                    int paramCount = method.getParameterCount();
                    try {
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
                        
                        logger.info("✅ Listener {}@{} completed successfully", 
                                annotationType.getSimpleName(), methodName);
                    } catch (java.lang.reflect.InvocationTargetException e) {
                        Throwable cause = e.getCause() != null ? e.getCause() : e;
                        logger.error("❌ Listener {}@{} threw exception: {} - {}", 
                                annotationType.getSimpleName(), methodName, 
                                cause.getClass().getName(), cause.getMessage());
                        logger.error("Full stacktrace:", cause);
                    } catch (Throwable t) {
                        logger.error("❌ Listener {}@{} invocation failed: {} - {}", 
                                annotationType.getSimpleName(), methodName,
                                t.getClass().getName(), t.getMessage());
                        logger.error("Full stacktrace:", t);
                    }
                    
                    // Log listener method end
                    kwLogger.endListener(listenerAction);
                    
                    logger.debug("Invoked @{} on {}#{}",
                            annotationType.getSimpleName(),
                            listener.getClass().getName(), method.getName());
                } finally {
                    currentThread.setContextClassLoader(previousCL);
                }
            }
        }
    }
}
