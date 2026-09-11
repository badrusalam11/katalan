package com.katalan.core.logging;

import org.codehaus.groovy.ast.AnnotationNode;
import org.codehaus.groovy.ast.ClassHelper;
import org.codehaus.groovy.ast.ClassNode;
import org.codehaus.groovy.ast.MethodNode;
import org.codehaus.groovy.ast.VariableScope;
import org.codehaus.groovy.ast.expr.ArgumentListExpression;
import org.codehaus.groovy.ast.expr.ConstantExpression;
import org.codehaus.groovy.ast.expr.DeclarationExpression;
import org.codehaus.groovy.ast.expr.Expression;
import org.codehaus.groovy.ast.expr.StaticMethodCallExpression;
import org.codehaus.groovy.ast.stmt.BlockStatement;
import org.codehaus.groovy.ast.stmt.CatchStatement;
import org.codehaus.groovy.ast.stmt.EmptyStatement;
import org.codehaus.groovy.ast.stmt.ExpressionStatement;
import org.codehaus.groovy.ast.stmt.ForStatement;
import org.codehaus.groovy.ast.stmt.IfStatement;
import org.codehaus.groovy.ast.stmt.ReturnStatement;
import org.codehaus.groovy.ast.stmt.Statement;
import org.codehaus.groovy.ast.stmt.TryCatchStatement;
import org.codehaus.groovy.ast.stmt.WhileStatement;
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
 * Compiler customizer that makes Test Listener step logging follow real
 * control flow.
 *
 * <p>Every statement inside a Katalon lifecycle method ({@code @BeforeTestSuite},
 * {@code @AfterTestSuite}, {@code @BeforeTestCase}, {@code @AfterTestCase} and
 * their {@code @SetUp} / {@code @TearDown} aliases) gets a
 * {@link ListenerStepTracer#step(String, int)} call injected immediately before
 * it. Branch bodies are instrumented in place, so a statement is logged only
 * when it is actually reached: an untaken {@code if} branch logs nothing, and a
 * method that hits an early {@code return} logs nothing past that point.</p>
 *
 * <p>Instrumentation is best-effort. A listener whose body cannot be rewritten
 * is left exactly as written rather than failing to compile.</p>
 */
public class ListenerStepTracingCustomizer extends CompilationCustomizer {

    private static final Logger logger = LoggerFactory.getLogger(ListenerStepTracingCustomizer.class);

    /** Lifecycle annotations whose method bodies are traced. */
    private static final Set<String> LIFECYCLE_ANNOTATIONS = new HashSet<>(Arrays.asList(
            "com.kms.katalon.core.annotation.BeforeTestSuite",
            "com.kms.katalon.core.annotation.AfterTestSuite",
            "com.kms.katalon.core.annotation.BeforeTestCase",
            "com.kms.katalon.core.annotation.AfterTestCase",
            "com.kms.katalon.core.annotation.SetUp",
            "com.kms.katalon.core.annotation.TearDown",
            "com.kms.katalon.core.annotation.SetupTestCase",
            "com.kms.katalon.core.annotation.TearDownTestCase"
    ));

    private static final ClassNode TRACER_TYPE = ClassHelper.make(ListenerStepTracer.class);

    public ListenerStepTracingCustomizer() {
        // CANONICALIZATION runs after variable scopes and global AST transforms
        // are settled but before bytecode generation.
        super(CompilePhase.CANONICALIZATION);
    }

    @Override
    public void call(SourceUnit source, GeneratorContext context, ClassNode classNode) {
        for (MethodNode method : classNode.getMethods()) {
            if (!isLifecycleMethod(method)) {
                continue;
            }
            Statement code = method.getCode();
            if (!(code instanceof BlockStatement)) {
                continue;
            }
            try {
                instrumentBlock((BlockStatement) code);
            } catch (Exception e) {
                logger.debug("Could not trace listener method {}#{}: {}",
                        classNode.getName(), method.getName(), e.getMessage());
            }
        }
    }

    private static boolean isLifecycleMethod(MethodNode method) {
        for (AnnotationNode annotation : method.getAnnotations()) {
            if (LIFECYCLE_ANNOTATIONS.contains(annotation.getClassNode().getName())) {
                return true;
            }
        }
        return false;
    }

    /** Rewrite a block's statement list in place, keeping its VariableScope. */
    private void instrumentBlock(BlockStatement block) {
        List<Statement> original = new ArrayList<>(block.getStatements());
        List<Statement> traced = new ArrayList<>(original.size() * 2);
        for (Statement statement : original) {
            instrument(statement, traced);
        }
        block.getStatements().clear();
        block.getStatements().addAll(traced);
    }

    /** Append {@code statement}, preceded by its trace call, to {@code out}. */
    private void instrument(Statement statement, List<Statement> out) {
        if (statement == null) {
            return;
        }
        int line = statement.getLineNumber();

        if (statement instanceof BlockStatement) {
            instrumentBlock((BlockStatement) statement);
            out.add(statement);
            return;
        }

        if (statement instanceof ExpressionStatement) {
            String actionText = GroovySourceParser.describeExpression(
                    ((ExpressionStatement) statement).getExpression());
            if (line > 0 && !GroovySourceParser.isNoiseAction(actionText)) {
                out.add(trace(actionText, line, statement));
            }
            out.add(statement);
            return;
        }

        if (statement instanceof ReturnStatement) {
            Expression value = ((ReturnStatement) statement).getExpression();
            String text = value == null ? "" : value.getText();
            if (line > 0) {
                out.add(trace("return " + ("null".equals(text) ? "" : text), line, statement));
            }
            out.add(statement);
            return;
        }

        if (statement instanceof IfStatement) {
            IfStatement ifStatement = (IfStatement) statement;
            out.add(trace("if (" + ifStatement.getBooleanExpression().getText() + ")", line, statement));
            ifStatement.setIfBlock(instrumentBranch(ifStatement.getIfBlock(), null));
            Statement elseBlock = ifStatement.getElseBlock();
            if (elseBlock != null && !(elseBlock instanceof EmptyStatement)) {
                ifStatement.setElseBlock(instrumentBranch(elseBlock, trace("else", line, elseBlock)));
            }
            out.add(ifStatement);
            return;
        }

        if (statement instanceof WhileStatement) {
            WhileStatement whileStatement = (WhileStatement) statement;
            out.add(trace("while (" + whileStatement.getBooleanExpression().getText() + ")", line, statement));
            whileStatement.setLoopBlock(instrumentBranch(whileStatement.getLoopBlock(), null));
            out.add(whileStatement);
            return;
        }

        if (statement instanceof ForStatement) {
            ForStatement forStatement = (ForStatement) statement;
            out.add(trace(forStatement.getVariable().getName() + " in "
                    + forStatement.getCollectionExpression().getText(), line, statement));
            forStatement.setLoopBlock(instrumentBranch(forStatement.getLoopBlock(), null));
            out.add(forStatement);
            return;
        }

        if (statement instanceof TryCatchStatement) {
            TryCatchStatement tryStatement = (TryCatchStatement) statement;
            out.add(trace("try", line, statement));
            tryStatement.setTryStatement(instrumentBranch(tryStatement.getTryStatement(), null));
            for (CatchStatement catchStatement : tryStatement.getCatchStatements()) {
                String label = "catch (" + catchStatement.getExceptionType().getName() + ")";
                catchStatement.setCode(instrumentBranch(catchStatement.getCode(),
                        trace(label, catchStatement.getLineNumber(), catchStatement)));
            }
            Statement finallyStatement = tryStatement.getFinallyStatement();
            if (finallyStatement != null && !(finallyStatement instanceof EmptyStatement)) {
                tryStatement.setFinallyStatement(instrumentBranch(finallyStatement,
                        trace("finally", finallyStatement.getLineNumber(), finallyStatement)));
            }
            out.add(tryStatement);
            return;
        }

        // Anything else (switch, throw, break, ...) runs untraced, as before.
        out.add(statement);
    }

    /**
     * Instrument a branch body, optionally prefixed by a marker step that is
     * logged only when the branch is entered.
     */
    private Statement instrumentBranch(Statement branch, Statement prefix) {
        if (branch == null || branch instanceof EmptyStatement) {
            return branch;
        }

        if (branch instanceof BlockStatement) {
            BlockStatement block = (BlockStatement) branch;
            instrumentBlock(block);
            if (prefix != null) {
                block.getStatements().add(0, prefix);
            }
            return block;
        }

        // A brace-less branch body. Wrapping a local declaration in a fresh
        // block would move it into a new scope, so leave those alone.
        if (isDeclaration(branch)) {
            return branch;
        }

        List<Statement> traced = new ArrayList<>(3);
        if (prefix != null) {
            traced.add(prefix);
        }
        instrument(branch, traced);
        BlockStatement wrapper = new BlockStatement(traced, new VariableScope());
        wrapper.setSourcePosition(branch);
        return wrapper;
    }

    private static boolean isDeclaration(Statement statement) {
        return statement instanceof ExpressionStatement
                && ((ExpressionStatement) statement).getExpression() instanceof DeclarationExpression;
    }

    /** Build {@code ListenerStepTracer.step("<actionText>", <line>)}. */
    private static Statement trace(String actionText, int line, Statement origin) {
        StaticMethodCallExpression call = new StaticMethodCallExpression(
                TRACER_TYPE,
                "step",
                new ArgumentListExpression(
                        new ConstantExpression(actionText),
                        new ConstantExpression(Math.max(line, 0))));
        ExpressionStatement statement = new ExpressionStatement(call);
        if (origin != null) {
            statement.setSourcePosition(origin);
        }
        return statement;
    }
}
