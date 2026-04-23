package com.katalan.core.engine;

import com.katalan.core.logging.XmlKeywordLogger;
import org.codehaus.groovy.ast.*;
import org.codehaus.groovy.ast.expr.*;
import org.codehaus.groovy.ast.stmt.*;
import org.codehaus.groovy.control.CompilePhase;
import org.codehaus.groovy.control.SourceUnit;
import org.codehaus.groovy.transform.ASTTransformation;
import org.codehaus.groovy.transform.GroovyASTTransformation;

import java.util.*;

/**
 * Groovy AST transformation that injects XmlKeywordLogger calls around every
 * statement to produce Katalon-style execution0.log output.
 * 
 * Wraps each statement (assignments, method calls, if/else blocks, loops) with:
 *   XmlKeywordLogger.getInstance().startKeyword("...", lineNumber, stepIndex)
 *   ... original statement ...
 *   XmlKeywordLogger.getInstance().endKeyword("...")
 */
@GroovyASTTransformation(phase = CompilePhase.SEMANTIC_ANALYSIS)
public class KeywordLoggingASTTransformation implements ASTTransformation {
    
    private int stepCounter = 0;
    
    @Override
    public void visit(ASTNode[] nodes, SourceUnit sourceUnit) {
        if (nodes == null || nodes.length == 0) return;
        
        System.err.println("[AST] KeywordLoggingASTTransformation.visit() triggered for: " + sourceUnit.getName());
        
        // Visit all classes in the script (including the main script class)
        List<ClassNode> classes = sourceUnit.getAST().getClasses();
        if (classes != null) {
            System.err.println("[AST] Found " + classes.size() + " class(es) to visit");
            for (ClassNode classNode : classes) {
                visitClass(classNode);
            }
        }
    }
    
    private void visitClass(ClassNode classNode) {
        // Visit all methods (including run() method for Groovy scripts)
        List<MethodNode> methods = classNode.getMethods();
        if (methods != null) {
            System.err.println("[AST] Visiting class: " + classNode.getName() + " with " + methods.size() + " method(s)");
            for (MethodNode method : methods) {
                if (method.getCode() != null) {
                    System.err.println("[AST] Transforming method: " + method.getName());
                    stepCounter = 0; // Reset step counter per method/test case
                    Statement transformed = transformStatement(method.getCode());
                    if (transformed != method.getCode()) {
                        method.setCode(transformed);
                    }
                }
            }
        }
    }
    
    private Statement transformStatement(Statement stmt) {
        if (stmt == null) return stmt;
        
        if (stmt instanceof BlockStatement) {
            return transformBlockStatement((BlockStatement) stmt);
        } else if (stmt instanceof ExpressionStatement) {
            return wrapWithLogging((ExpressionStatement) stmt);
        } else if (stmt instanceof IfStatement) {
            return transformIfStatement((IfStatement) stmt);
        } else if (stmt instanceof WhileStatement) {
            return transformWhileStatement((WhileStatement) stmt);
        } else if (stmt instanceof ForStatement) {
            return transformForStatement((ForStatement) stmt);
        } else if (stmt instanceof TryCatchStatement) {
            return transformTryCatchStatement((TryCatchStatement) stmt);
        } else if (stmt instanceof ReturnStatement) {
            return wrapReturn((ReturnStatement) stmt);
        }
        
        // Other statement types (break, continue, etc.) — pass through
        return stmt;
    }
    
    private BlockStatement transformBlockStatement(BlockStatement block) {
        List<Statement> newStatements = new ArrayList<>();
        for (Statement stmt : block.getStatements()) {
            newStatements.add(transformStatement(stmt));
        }
        BlockStatement newBlock = new BlockStatement();
        newBlock.getStatements().addAll(newStatements);
        return newBlock;
    }
    
    private Statement wrapWithLogging(ExpressionStatement stmt) {
        Expression expr = stmt.getExpression();
        int lineNumber = stmt.getLineNumber();
        stepCounter++;
        
        String actionText = buildActionText(expr);
        
        // Build: XmlKeywordLogger.getInstance().startKeyword("...", lineNumber, stepIndex)
        MethodCallExpression startCall = new MethodCallExpression(
            new MethodCallExpression(
                new ClassExpression(ClassHelper.make(XmlKeywordLogger.class)),
                "getInstance",
                ArgumentListExpression.EMPTY_ARGUMENTS
            ),
            "startKeyword",
            new ArgumentListExpression(
                new ConstantExpression(actionText),
                new ConstantExpression(lineNumber),
                new ConstantExpression(stepCounter)
            )
        );
        
        // Build: XmlKeywordLogger.getInstance().endKeyword("...")
        MethodCallExpression endCall = new MethodCallExpression(
            new MethodCallExpression(
                new ClassExpression(ClassHelper.make(XmlKeywordLogger.class)),
                "getInstance",
                ArgumentListExpression.EMPTY_ARGUMENTS
            ),
            "endKeyword",
            new ArgumentListExpression(
                new ConstantExpression(actionText)
            )
        );
        
        // Wrap in try/finally to ensure endKeyword is called even on exception
        BlockStatement wrappedBlock = new BlockStatement();
        wrappedBlock.addStatement(new ExpressionStatement(startCall));
        
        TryCatchStatement tryFinally = new TryCatchStatement(
            stmt,
            new EmptyStatement()
        );
        tryFinally.setFinallyStatement(new ExpressionStatement(endCall));
        
        wrappedBlock.addStatement(tryFinally);
        return wrappedBlock;
    }
    
    private Statement wrapReturn(ReturnStatement ret) {
        // Don't wrap return statements — they end control flow
        return ret;
    }
    
    private Statement transformIfStatement(IfStatement ifStmt) {
        Statement ifBlock = transformStatement(ifStmt.getIfBlock());
        Statement elseBlock = transformStatement(ifStmt.getElseBlock());
        
        int lineNumber = ifStmt.getLineNumber();
        stepCounter++;
        String actionText = "if (" + ifStmt.getBooleanExpression().getText() + ")";
        
        // Wrap the condition check as a keyword
        MethodCallExpression startIf = new MethodCallExpression(
            new MethodCallExpression(
                new ClassExpression(ClassHelper.make(XmlKeywordLogger.class)),
                "getInstance",
                ArgumentListExpression.EMPTY_ARGUMENTS
            ),
            "startKeyword",
            new ArgumentListExpression(
                new ConstantExpression(actionText),
                new ConstantExpression(lineNumber),
                new ConstantExpression(stepCounter)
            )
        );
        
        MethodCallExpression endIf = new MethodCallExpression(
            new MethodCallExpression(
                new ClassExpression(ClassHelper.make(XmlKeywordLogger.class)),
                "getInstance",
                ArgumentListExpression.EMPTY_ARGUMENTS
            ),
            "endKeyword",
            new ArgumentListExpression(
                new ConstantExpression(actionText)
            )
        );
        
        BlockStatement wrapped = new BlockStatement();
        wrapped.addStatement(new ExpressionStatement(startIf));
        
        IfStatement newIf = new IfStatement(
            ifStmt.getBooleanExpression(),
            ifBlock,
            elseBlock
        );
        
        TryCatchStatement tryFinally = new TryCatchStatement(
            newIf,
            new EmptyStatement()
        );
        tryFinally.setFinallyStatement(new ExpressionStatement(endIf));
        
        wrapped.addStatement(tryFinally);
        return wrapped;
    }
    
    private Statement transformWhileStatement(WhileStatement whileStmt) {
        Statement loopBlock = transformStatement(whileStmt.getLoopBlock());
        WhileStatement newWhile = new WhileStatement(
            whileStmt.getBooleanExpression(),
            loopBlock
        );
        return newWhile;
    }
    
    private Statement transformForStatement(ForStatement forStmt) {
        Statement loopBlock = transformStatement(forStmt.getLoopBlock());
        ForStatement newFor = new ForStatement(
            forStmt.getVariable(),
            forStmt.getCollectionExpression(),
            loopBlock
        );
        return newFor;
    }
    
    private Statement transformTryCatchStatement(TryCatchStatement tryStmt) {
        Statement tryBlock = transformStatement(tryStmt.getTryStatement());
        Statement finallyBlock = transformStatement(tryStmt.getFinallyStatement());
        
        TryCatchStatement newTry = new TryCatchStatement(
            tryBlock,
            finallyBlock
        );
        for (CatchStatement catchStmt : tryStmt.getCatchStatements()) {
            Statement catchBlock = transformStatement(catchStmt.getCode());
            newTry.addCatch(new CatchStatement(
                catchStmt.getVariable(),
                catchBlock
            ));
        }
        return newTry;
    }
    
    /**
     * Build human-readable action text from an expression (for log messages).
     */
    private String buildActionText(Expression expr) {
        if (expr instanceof BinaryExpression) {
            BinaryExpression bin = (BinaryExpression) expr;
            if (bin.getOperation().getText().equals("=")) {
                // Assignment: varName = value
                return bin.getLeftExpression().getText() + " = " + simplifyExpression(bin.getRightExpression());
            }
        } else if (expr instanceof MethodCallExpression) {
            MethodCallExpression methodCall = (MethodCallExpression) expr;
            String method = methodCall.getMethodAsString();
            String args = methodCall.getArguments().getText();
            if (methodCall.isImplicitThis()) {
                return method + "(" + args + ")";
            } else {
                return methodCall.getObjectExpression().getText() + "." + method + "(" + args + ")";
            }
        } else if (expr instanceof DeclarationExpression) {
            DeclarationExpression decl = (DeclarationExpression) expr;
            return decl.getLeftExpression().getText() + " = " + simplifyExpression(decl.getRightExpression());
        }
        
        // Fallback
        return expr.getText();
    }
    
    /**
     * Simplify complex expressions for log display (truncate long literals, etc.).
     */
    private String simplifyExpression(Expression expr) {
        String text = expr.getText();
        // If it's a long string/closure, show abbreviated form
        if (text.length() > 80) {
            return text.substring(0, 77) + "...";
        }
        return text;
    }
}
