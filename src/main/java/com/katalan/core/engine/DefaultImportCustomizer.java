package com.katalan.core.engine;

import org.codehaus.groovy.ast.ClassHelper;
import org.codehaus.groovy.ast.ClassNode;
import org.codehaus.groovy.ast.ModuleNode;
import org.codehaus.groovy.classgen.GeneratorContext;
import org.codehaus.groovy.control.CompilePhase;
import org.codehaus.groovy.control.SourceUnit;
import org.codehaus.groovy.control.customizers.CompilationCustomizer;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Adds an import only when the source does not import that simple name itself.
 *
 * Groovy's {@code ImportCustomizer.addImports(..)} replaces a source's own import with the same
 * alias. That is fine for a test case script that relies on Katalon's implicit imports, but a
 * keyword compiled by the same class loader that declares
 * {@code import com.kms.katalon.core.context.TestCaseContext} would silently get katalan's class
 * instead - and a listener calling {@code CustomReport.AfterTestCase(testCaseContext)} then fails
 * with MissingMethodException.
 */
public class DefaultImportCustomizer extends CompilationCustomizer {

    private final Map<String, String> imports = new LinkedHashMap<>();

    public DefaultImportCustomizer() {
        super(CompilePhase.CONVERSION);
    }

    public DefaultImportCustomizer addImport(String className) {
        imports.put(className.substring(className.lastIndexOf('.') + 1), className);
        return this;
    }

    @Override
    public void call(SourceUnit source, GeneratorContext context, ClassNode classNode) {
        ModuleNode module = source.getAST();
        for (Map.Entry<String, String> e : imports.entrySet()) {
            if (module.getImport(e.getKey()) == null && !declaresClass(module, e.getKey())) {
                module.addImport(e.getKey(), ClassHelper.make(e.getValue()));
            }
        }
    }

    private static boolean declaresClass(ModuleNode module, String simpleName) {
        for (ClassNode c : module.getClasses()) {
            if (simpleName.equals(c.getNameWithoutPackage())) {
                return true;
            }
        }
        return false;
    }
}
