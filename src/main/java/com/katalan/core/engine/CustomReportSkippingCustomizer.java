package com.katalan.core.engine;

import com.katalan.core.config.CustomReportConfig;
import org.codehaus.groovy.ast.ClassNode;
import org.codehaus.groovy.ast.MethodNode;
import org.codehaus.groovy.ast.expr.Expression;
import org.codehaus.groovy.ast.expr.MethodCallExpression;
import org.codehaus.groovy.ast.expr.StaticMethodCallExpression;
import org.codehaus.groovy.ast.stmt.BlockStatement;
import org.codehaus.groovy.ast.stmt.CatchStatement;
import org.codehaus.groovy.ast.stmt.ExpressionStatement;
import org.codehaus.groovy.ast.stmt.IfStatement;
import org.codehaus.groovy.ast.stmt.LoopingStatement;
import org.codehaus.groovy.ast.stmt.Statement;
import org.codehaus.groovy.ast.stmt.TryCatchStatement;
import org.codehaus.groovy.classgen.GeneratorContext;
import org.codehaus.groovy.control.CompilePhase;
import org.codehaus.groovy.control.SourceUnit;
import org.codehaus.groovy.control.customizers.CompilationCustomizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Drops calls to third-party custom report exporters from Test Listener sources
 * when {@link CustomReportConfig#isSkipCustomReport()} is on, so a run produces
 * only katalan's own report.
 *
 * <p>The removal is surgical: only the export call statement itself goes away.
 * Everything else in the listener method still runs, which matters because these
 * methods usually mix the export in with unrelated setup - proxy configuration,
 * hostname logging, screenshots.</p>
 *
 * <p>A call is removed when it is a statement on its own and names one of
 * {@link #EXPORT_METHODS}. A call whose result is assigned or used in an
 * expression is left alone, since dropping it would change the surrounding
 * code's meaning.</p>
 */
public class CustomReportSkippingCustomizer extends CompilationCustomizer {

    private static final Logger logger = LoggerFactory.getLogger(CustomReportSkippingCustomizer.class);

    /** Exporter entry points. CSReport is the one Katalon projects actually wire up. */
    private static final Set<String> EXPORT_METHODS = new HashSet<>(Arrays.asList(
            "exportKatalonReports",
            "exportKatalonReport"
    ));

    public CustomReportSkippingCustomizer() {
        super(CompilePhase.CANONICALIZATION);
    }

    @Override
    public void call(SourceUnit source, GeneratorContext context, ClassNode classNode) {
        if (!CustomReportConfig.isSkipCustomReport()) {
            return;
        }
        for (MethodNode method : classNode.getMethods()) {
            Statement code = method.getCode();
            if (!(code instanceof BlockStatement)) {
                continue;
            }
            try {
                int removed = removeExportCalls((BlockStatement) code);
                if (removed > 0) {
                    logger.info("Skipped {} custom report export call(s) in {}#{} ({}=true)",
                            removed, classNode.getName(), method.getName(), CustomReportConfig.KEY);
                }
            } catch (Exception e) {
                logger.debug("Could not strip custom report call in {}#{}: {}",
                        classNode.getName(), method.getName(), e.getMessage());
            }
        }
    }

    /** Remove export-call statements from this block and every block nested in it. */
    private int removeExportCalls(BlockStatement block) {
        int removed = 0;
        List<Statement> kept = new ArrayList<>(block.getStatements().size());
        for (Statement statement : block.getStatements()) {
            if (isExportCall(statement)) {
                removed++;
                continue;
            }
            removed += descend(statement);
            kept.add(statement);
        }
        block.getStatements().clear();
        block.getStatements().addAll(kept);
        return removed;
    }

    /** Recurse into the bodies of the control-flow statements Groovy listeners use. */
    private int descend(Statement statement) {
        if (statement instanceof BlockStatement) {
            return removeExportCalls((BlockStatement) statement);
        }
        if (statement instanceof IfStatement) {
            IfStatement ifStatement = (IfStatement) statement;
            return descend(ifStatement.getIfBlock()) + descend(ifStatement.getElseBlock());
        }
        if (statement instanceof TryCatchStatement) {
            TryCatchStatement tryStatement = (TryCatchStatement) statement;
            int removed = descend(tryStatement.getTryStatement())
                    + descend(tryStatement.getFinallyStatement());
            for (CatchStatement catchStatement : tryStatement.getCatchStatements()) {
                removed += descend(catchStatement.getCode());
            }
            return removed;
        }
        if (statement instanceof LoopingStatement) {
            return descend(((LoopingStatement) statement).getLoopBlock());
        }
        return 0;
    }

    /** True when the statement is nothing but a call to a custom report exporter. */
    private static boolean isExportCall(Statement statement) {
        if (!(statement instanceof ExpressionStatement)) {
            return false;
        }
        Expression expression = ((ExpressionStatement) statement).getExpression();
        if (expression instanceof MethodCallExpression) {
            return EXPORT_METHODS.contains(((MethodCallExpression) expression).getMethodAsString());
        }
        if (expression instanceof StaticMethodCallExpression) {
            return EXPORT_METHODS.contains(((StaticMethodCallExpression) expression).getMethod());
        }
        return false;
    }
}
