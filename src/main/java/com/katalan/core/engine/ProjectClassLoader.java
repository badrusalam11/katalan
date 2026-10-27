package com.katalan.core.engine;

import groovy.lang.GroovyClassLoader;

/**
 * The one class loader that owns the project's Keywords/ and Include/scripts/groovy classes.
 *
 * Katalon Studio compiles a project into a single class loader, so a keyword class such as
 * {@code common.CustomReport} exists once per run and its static state is shared by Test
 * Listeners, the suite script, test case scripts and BDD step definitions. Every katalan
 * loader that runs project code is created as a child of this loader instead of putting
 * Keywords/ on its own classpath, which would compile a second, independent copy.
 */
public final class ProjectClassLoader {

    private static volatile GroovyClassLoader current;

    private ProjectClassLoader() {
    }

    static void set(GroovyClassLoader loader) {
        current = loader;
    }

    /** The project loader, or null before the engine has created it. */
    public static GroovyClassLoader get() {
        return current;
    }
}
