package com.katalan.core.engine;

import net.bytebuddy.agent.ByteBuddyAgent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.instrument.Instrumentation;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.jar.JarFile;

/**
 * Appends project-local JARs (Drivers/, Libs/, bin/lib/) to the <b>system
 * classloader</b> using the {@code java.lang.instrument} API, via ByteBuddy's
 * self-attach agent.
 *
 * <p>Why do we need this? Some Katalon-era keywords (e.g.
 * {@code denstoo.reporting.CSReport}) do:
 * <pre>
 *   new GroovyShell().evaluate("import static denstoo.reporting.CSReport.*")
 * </pre>
 * The default {@code GroovyShell()} uses {@code GroovyShell.class.getClassLoader()}
 * as the parent of its internal GroovyClassLoader. That is the katalan-runner's
 * own application classloader, which knows nothing about project JARs. The
 * thread context classloader is ignored by that code path.</p>
 *
 * <p>The cleanest fix that mirrors Katalon's own behaviour is to make the
 * project JARs visible to the <b>system</b> classloader so every nested
 * GroovyShell can resolve them. That is exactly what
 * {@link Instrumentation#appendToSystemClassLoaderSearch(JarFile)} does.</p>
 */
public final class SystemClasspathAppender {

    private static final Logger logger = LoggerFactory.getLogger(SystemClasspathAppender.class);

    private static final AtomicBoolean ATTACH_FAILED = new AtomicBoolean(false);
    private static volatile Instrumentation INSTRUMENTATION;

    private SystemClasspathAppender() {}

    /**
     * Append every {@code .jar} found in {@code Drivers/}, {@code Libs/} and
     * {@code bin/lib/} under the given project path to the system classloader.
     * Safe to call multiple times; failures are logged and swallowed.
     */
    public static void appendProjectJars(Path projectPath) {
        if (projectPath == null) return;
        Instrumentation inst = getInstrumentation();
        if (inst == null) return;

        for (String folder : new String[]{"Drivers", "Libs", "bin/lib"}) {
            Path dir = projectPath.resolve(folder);
            if (!Files.isDirectory(dir)) continue;
            try {
                Files.walk(dir, 1)
                        .filter(p -> p.toString().toLowerCase().endsWith(".jar"))
                        .forEach(jar -> appendJar(inst, jar));
            } catch (Exception e) {
                logger.warn("Could not scan {} for JARs: {}", dir, e.getMessage());
            }
        }
    }

    private static void appendJar(Instrumentation inst, Path jar) {
        try {
            inst.appendToSystemClassLoaderSearch(new JarFile(jar.toFile()));
            logger.info("Appended to system classpath: {}", jar.getFileName());
        } catch (Throwable t) {
            logger.warn("Could not append {} to system classpath: {}", jar.getFileName(), t.getMessage());
        }
    }

    /** Lazily self-attaches the ByteBuddy agent and caches the {@link Instrumentation}. */
    private static synchronized Instrumentation getInstrumentation() {
        if (INSTRUMENTATION != null) return INSTRUMENTATION;
        if (ATTACH_FAILED.get()) return null;
        try {
            INSTRUMENTATION = ByteBuddyAgent.install();
            return INSTRUMENTATION;
        } catch (Throwable t) {
            ATTACH_FAILED.set(true);
            logger.warn("Could not self-attach instrumentation agent ({}): project JARs " +
                    "will not be visible to nested GroovyShell instances. " +
                    "Run with -Djdk.attach.allowAttachSelf=true if needed.", t.getMessage());
            return null;
        }
    }
}
