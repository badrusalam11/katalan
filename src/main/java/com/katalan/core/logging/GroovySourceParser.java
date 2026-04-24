package com.katalan.core.logging;

import org.codehaus.groovy.ast.*;
import org.codehaus.groovy.ast.expr.*;
import org.codehaus.groovy.ast.stmt.*;
import org.codehaus.groovy.control.CompilerConfiguration;
import org.codehaus.groovy.control.SourceUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Parses Groovy source files to extract keyword execution details
 * for XmlKeywordLogger (execution0.log generation).
 * 
 * Post-processes test case source code to generate startKeyword/endKeyword
 * records matching Katalon Studio format.
 */
public class GroovySourceParser {
    
    private static final Logger log = LoggerFactory.getLogger(GroovySourceParser.class);
    
    private int stepCounter = 0;
    private int nestedLevel = 1; // Start at 1 for test case statements
    private XmlKeywordLogger logger;
    
    // Storage for listener method statements
    private static final Map<String, Map<String, List<StatementInfo>>> listenerStatements = new HashMap<>();
    
    /**
     * Represents a statement with its metadata for logging.
     */
    public static class StatementInfo {
        public final String actionText;
        public final int lineNumber;
        public final String type; // "expression", "if", "while", "return", etc.
        
        public StatementInfo(String actionText, int lineNumber, String type) {
            this.actionText = actionText;
            this.lineNumber = lineNumber;
            this.type = type;
        }
    }
    
    /**
     * Parse test case source file and populate keyword logs.
     */
    public void parseAndLogTestCase(Path sourceFile, XmlKeywordLogger logger) {
        this.logger = logger;
        this.stepCounter = 0;
        this.nestedLevel = 1;
        
        try {
            if (!Files.exists(sourceFile)) {
                log.warn("Source file not found: {}", sourceFile);
                return;
            }
            
            String source = Files.readString(sourceFile);
            
            // Strip problematic imports that don't exist in katalan
            source = stripProblematicImports(source);
            
            // Parse Groovy source using AST
            CompilerConfiguration config = new CompilerConfiguration();
            config.setTolerance(10); // Allow some errors
            org.codehaus.groovy.control.CompilationUnit unit = 
                new org.codehaus.groovy.control.CompilationUnit(config);
            
            SourceUnit sourceUnit = unit.addSource(sourceFile.getFileName().toString(), source);
            unit.compile(org.codehaus.groovy.control.Phases.CONVERSION); // Use earlier phase to avoid resolution errors
            
            ModuleNode moduleNode = sourceUnit.getAST();
            if (moduleNode != null) {
                // Try to get script body first (for script files without class definitions)
                Statement scriptStatement = moduleNode.getStatementBlock();
                boolean handled = false;
                if (scriptStatement instanceof BlockStatement
                        && !((BlockStatement) scriptStatement).getStatements().isEmpty()) {
                    log.debug("Processing script body with {} statements",
                            ((BlockStatement) scriptStatement).getStatements().size());
                    visitStatement(scriptStatement);
                    handled = true;
                }
                
                // Only process classes if script body had no statements
                // (Groovy wraps script body inside a generated class' run() method,
                //  so visiting both would double-log every statement.)
                if (!handled) {
                    List<ClassNode> classes = moduleNode.getClasses();
                    for (ClassNode classNode : classes) {
                        // Find the main run() method or script body
                        for (MethodNode method : classNode.getMethods()) {
                            if (method.getName().equals("run") || method.getName().equals("runScript")) {
                                Statement code = method.getCode();
                                if (code != null) {
                                    visitStatement(code);
                                }
                            }
                        }
                    }
                }
            }
            
            log.info("Parsed {} statements from source file: {}", stepCounter, sourceFile.getFileName());
            
        } catch (Exception e) {
            log.warn("Could not parse source file {}: {}", sourceFile, e.getMessage());
        }
    }
    
    /**
     * Parse listener source file and extract statement structure for each method.
     * This stores the statements so they can be logged when the listener methods are invoked.
     */
    public static void parseListenerSource(String sourceFilePath, String className) {
        try {
            Path sourcePath = Path.of(sourceFilePath);
            if (!Files.exists(sourcePath)) {
                log.warn("Listener source file not found: {}", sourceFilePath);
                return;
            }
            
            String source = Files.readString(sourcePath);
            source = stripProblematicImportsStatic(source);
            
            // Parse with AST
            CompilerConfiguration config = new CompilerConfiguration();
            config.setTolerance(10);
            org.codehaus.groovy.control.CompilationUnit unit = 
                new org.codehaus.groovy.control.CompilationUnit(config);
            
            SourceUnit sourceUnit = unit.addSource(sourcePath.getFileName().toString(), source);
            unit.compile(org.codehaus.groovy.control.Phases.CONVERSION);
            
            ModuleNode moduleNode = sourceUnit.getAST();
            if (moduleNode == null) return;
            
            // Process all classes in the source
            Map<String, List<StatementInfo>> methodStatements = new HashMap<>();
            for (ClassNode classNode : moduleNode.getClasses()) {
                // Process all methods
                for (MethodNode method : classNode.getMethods()) {
                    String methodName = method.getName();
                    
                    // Skip synthetic and internal methods
                    if (methodName.startsWith("$") || methodName.equals("<init>") || 
                        methodName.equals("<clinit>")) {
                        continue;
                    }
                    
                    List<StatementInfo> statements = new ArrayList<>();
                    Statement code = method.getCode();
                    if (code != null) {
                        extractStatements(code, statements);
                    }
                    
                    if (!statements.isEmpty()) {
                        methodStatements.put(methodName, statements);
                        log.debug("Extracted {} statements from listener method: {}.{}", 
                                statements.size(), className, methodName);
                    }
                }
            }
            
            if (!methodStatements.isEmpty()) {
                listenerStatements.put(className, methodStatements);
                log.info("Parsed listener source {} with {} method(s)", 
                        className, methodStatements.size());
            }
            
        } catch (Exception e) {
            log.warn("Could not parse listener source {}: {}", sourceFilePath, e.getMessage());
        }
    }
    
    /**
     * Extract statement information from AST recursively.
     */
    private static void extractStatements(Statement stmt, List<StatementInfo> statements) {
        if (stmt == null) return;
        
        if (stmt instanceof BlockStatement) {
            for (Statement s : ((BlockStatement) stmt).getStatements()) {
                extractStatements(s, statements);
            }
        } else if (stmt instanceof ExpressionStatement) {
            Expression expr = ((ExpressionStatement) stmt).getExpression();
            int lineNumber = stmt.getLineNumber();
            if (lineNumber > 0) {
                String actionText = buildActionTextStatic(expr);
                if (!shouldSkipActionStatic(actionText)) {
                    statements.add(new StatementInfo(actionText, lineNumber, "expression"));
                }
            }
        } else if (stmt instanceof IfStatement) {
            IfStatement ifStmt = (IfStatement) stmt;
            int lineNumber = ifStmt.getLineNumber();
            String condition = ifStmt.getBooleanExpression().getText();
            statements.add(new StatementInfo("if (" + condition + ")", lineNumber, "if"));
            extractStatements(ifStmt.getIfBlock(), statements);
            Statement elseBlock = ifStmt.getElseBlock();
            if (elseBlock != null && !(elseBlock instanceof EmptyStatement)) {
                statements.add(new StatementInfo("else", lineNumber, "else"));
                extractStatements(elseBlock, statements);
            }
        } else if (stmt instanceof WhileStatement) {
            WhileStatement whileStmt = (WhileStatement) stmt;
            String condition = whileStmt.getBooleanExpression().getText();
            statements.add(new StatementInfo("while (" + condition + ")", 
                    whileStmt.getLineNumber(), "while"));
            extractStatements(whileStmt.getLoopBlock(), statements);
        } else if (stmt instanceof ForStatement) {
            ForStatement forStmt = (ForStatement) stmt;
            String variable = forStmt.getVariable().getName();
            String collection = forStmt.getCollectionExpression().getText();
            statements.add(new StatementInfo(variable + " in " + collection, 
                    forStmt.getLineNumber(), "for"));
            extractStatements(forStmt.getLoopBlock(), statements);
        } else if (stmt instanceof TryCatchStatement) {
            TryCatchStatement tryStmt = (TryCatchStatement) stmt;
            statements.add(new StatementInfo("try", tryStmt.getLineNumber(), "try"));
            extractStatements(tryStmt.getTryStatement(), statements);
            for (CatchStatement catchStmt : tryStmt.getCatchStatements()) {
                String exceptionType = catchStmt.getVariable().getType().getName();
                statements.add(new StatementInfo("catch (" + exceptionType + ")", 
                        catchStmt.getLineNumber(), "catch"));
                extractStatements(catchStmt.getCode(), statements);
            }
            Statement finallyStmt = tryStmt.getFinallyStatement();
            if (finallyStmt != null && !(finallyStmt instanceof EmptyStatement)) {
                statements.add(new StatementInfo("finally", 
                        finallyStmt.getLineNumber(), "finally"));
                extractStatements(finallyStmt, statements);
            }
        } else if (stmt instanceof ReturnStatement) {
            ReturnStatement ret = (ReturnStatement) stmt;
            int lineNumber = ret.getLineNumber();
            if (lineNumber > 0) {
                String returnValue = ret.getExpression().getText();
                String actionText = "return " + (returnValue.equals("null") ? "" : returnValue);
                statements.add(new StatementInfo(actionText, lineNumber, "return"));
            }
        }
    }
    
    /**
     * Get stored statements for a listener method.
     * Returns null if not found.
     */
    public static List<StatementInfo> getListenerMethodStatements(String className, String methodName) {
        Map<String, List<StatementInfo>> methods = listenerStatements.get(className);
        if (methods == null) return null;
        return methods.get(methodName);
    }
    
    private static String buildActionTextStatic(Expression expr) {
        if (expr instanceof BinaryExpression) {
            BinaryExpression bin = (BinaryExpression) expr;
            String op = bin.getOperation().getText();
            
            if (op.equals("=")) {
                String left = bin.getLeftExpression().getText();
                String right = simplifyExpressionStatic(bin.getRightExpression());
                return left + " = " + right;
            } else {
                return bin.getLeftExpression().getText() + " " + op + " " + 
                       bin.getRightExpression().getText();
            }
        } else if (expr instanceof MethodCallExpression) {
            MethodCallExpression call = (MethodCallExpression) expr;
            String objectText = call.getObjectExpression().getText();
            String method = call.getMethodAsString();
            
            // Simplify common patterns
            if (objectText.equals("this")) {
                return method + "(...)";
            }
            return objectText + "." + method + "(...)";
        } else if (expr instanceof StaticMethodCallExpression) {
            StaticMethodCallExpression call = (StaticMethodCallExpression) expr;
            return call.getOwnerType().getName() + "." + call.getMethod() + "(...)";
        } else if (expr instanceof ConstructorCallExpression) {
            ConstructorCallExpression ctor = (ConstructorCallExpression) expr;
            return "new " + ctor.getType().getName() + "(...)";
        } else if (expr instanceof ClosureExpression) {
            return "{ ... }";
        }
        
        String text = expr.getText();
        if (text.length() > 80) {
            return text.substring(0, 77) + "...";
        }
        return text;
    }
    
    private static String simplifyExpressionStatic(Expression expr) {
        if (expr instanceof ConstantExpression) {
            Object value = ((ConstantExpression) expr).getValue();
            if (value instanceof String) {
                String s = (String) value;
                if (s.length() > 40) {
                    return "\"" + s.substring(0, 37) + "...\"";
                }
                return "\"" + s + "\"";
            }
            return String.valueOf(value);
        } else if (expr instanceof ClosureExpression) {
            return "{ ... }";
        }
        
        String text = expr.getText();
        if (text.length() > 40) {
            return text.substring(0, 37) + "...";
        }
        return text;
    }
    
    private static String stripProblematicImportsStatic(String source) {
        String[] problematicPatterns = {
            "com.kms.katalon.core.testng.keyword.TestNGBuiltinKeywords",
            "com.kms.katalon.core.mobile.keyword.MobileBuiltInKeywords",
            "com.kms.katalon.core.cucumber.keyword.CucumberBuiltinKeywords",
            "com.kms.katalon.core.windows.keyword.WindowsBuiltinKeywords",
            "com.kms.katalon.core.testdata.TestDataFactory",
            "com.kms.katalon.core.checkpoint.CheckpointFactory",
            "com.kms.katalon.core.testcase.TestCaseFactory",
            "com.kms.katalon.core.testobject.ObjectRepository"
        };
        
        for (String pattern : problematicPatterns) {
            source = source.replaceAll("(?m)^(\\s*)import\\s+" + java.util.regex.Pattern.quote(pattern) + ".*$", 
                                      "$1// $0 // katalan: stripped");
        }
        
        return source;
    }
    
    private static boolean shouldSkipActionStatic(String actionText) {
        if (actionText == null || actionText.trim().isEmpty()) return true;
        if (actionText.matches("^[a-zA-Z_][a-zA-Z0-9_]*$")) return true;
        if (actionText.startsWith("$")) return true;
        if (actionText.startsWith("this.")) return true;
        return false;
    }
    
    private void visitStatement(Statement stmt) {
        if (stmt == null) return;
        
        if (stmt instanceof BlockStatement) {
            visitBlockStatement((BlockStatement) stmt);
        } else if (stmt instanceof ExpressionStatement) {
            visitExpressionStatement((ExpressionStatement) stmt);
        } else if (stmt instanceof IfStatement) {
            visitIfStatement((IfStatement) stmt);
        } else if (stmt instanceof WhileStatement) {
            visitWhileStatement((WhileStatement) stmt);
        } else if (stmt instanceof ForStatement) {
            visitForStatement((ForStatement) stmt);
        } else if (stmt instanceof TryCatchStatement) {
            visitTryCatchStatement((TryCatchStatement) stmt);
        } else if (stmt instanceof ReturnStatement) {
            visitReturnStatement((ReturnStatement) stmt);
        }
    }
    
    private void visitBlockStatement(BlockStatement block) {
        for (Statement stmt : block.getStatements()) {
            visitStatement(stmt);
        }
    }
    
    private void visitExpressionStatement(ExpressionStatement stmt) {
        Expression expr = stmt.getExpression();
        int lineNumber = stmt.getLineNumber();
        
        if (lineNumber <= 0) return; // Skip invalid lines
        
        stepCounter++;
        String actionText = buildActionText(expr);
        
        // Skip if action text is too generic or internal
        if (shouldSkipAction(actionText)) {
            stepCounter--;
            return;
        }
        
        logger.startKeyword(actionText, lineNumber, stepCounter);
        logger.endKeyword(actionText);
    }
    
    private void visitIfStatement(IfStatement ifStmt) {
        int lineNumber = ifStmt.getLineNumber();
        stepCounter++;
        
        String condition = ifStmt.getBooleanExpression().getText();
        String actionText = "if (" + condition + ")";
        
        logger.startKeyword(actionText, lineNumber, stepCounter);
        
        // Visit if block
        nestedLevel++;
        visitStatement(ifStmt.getIfBlock());
        nestedLevel--;
        
        logger.endKeyword(actionText);
        
        // Visit else block if exists
        Statement elseBlock = ifStmt.getElseBlock();
        if (elseBlock != null && !(elseBlock instanceof EmptyStatement)) {
            stepCounter++;
            logger.startKeyword("else", lineNumber, stepCounter);
            nestedLevel++;
            visitStatement(elseBlock);
            nestedLevel--;
            logger.endKeyword("else");
        }
    }
    
    private void visitWhileStatement(WhileStatement whileStmt) {
        int lineNumber = whileStmt.getLineNumber();
        stepCounter++;
        
        String condition = whileStmt.getBooleanExpression().getText();
        String actionText = "while (" + condition + ")";
        
        logger.startKeyword(actionText, lineNumber, stepCounter);
        nestedLevel++;
        visitStatement(whileStmt.getLoopBlock());
        nestedLevel--;
        logger.endKeyword(actionText);
    }
    
    private void visitForStatement(ForStatement forStmt) {
        int lineNumber = forStmt.getLineNumber();
        stepCounter++;
        
        String variable = forStmt.getVariable().getName();
        String collection = forStmt.getCollectionExpression().getText();
        String actionText = variable + " in " + collection;
        
        logger.startKeyword(actionText, lineNumber, stepCounter);
        nestedLevel++;
        visitStatement(forStmt.getLoopBlock());
        nestedLevel--;
        logger.endKeyword(actionText);
    }
    
    private void visitTryCatchStatement(TryCatchStatement tryStmt) {
        int lineNumber = tryStmt.getLineNumber();
        stepCounter++;
        
        logger.startKeyword("try", lineNumber, stepCounter);
        nestedLevel++;
        visitStatement(tryStmt.getTryStatement());
        nestedLevel--;
        logger.endKeyword("try");
        
        for (CatchStatement catchStmt : tryStmt.getCatchStatements()) {
            stepCounter++;
            String exceptionType = catchStmt.getVariable().getType().getName();
            logger.startKeyword("catch (" + exceptionType + ")", catchStmt.getLineNumber(), stepCounter);
            nestedLevel++;
            visitStatement(catchStmt.getCode());
            nestedLevel--;
            logger.endKeyword("catch (" + exceptionType + ")");
        }
        
        Statement finallyStmt = tryStmt.getFinallyStatement();
        if (finallyStmt != null && !(finallyStmt instanceof EmptyStatement)) {
            stepCounter++;
            logger.startKeyword("finally", finallyStmt.getLineNumber(), stepCounter);
            nestedLevel++;
            visitStatement(finallyStmt);
            nestedLevel--;
            logger.endKeyword("finally");
        }
    }
    
    private void visitReturnStatement(ReturnStatement ret) {
        int lineNumber = ret.getLineNumber();
        if (lineNumber <= 0) return;
        
        stepCounter++;
        String returnValue = ret.getExpression().getText();
        String actionText = "return " + (returnValue.equals("null") ? "" : returnValue);
        
        logger.startKeyword(actionText, lineNumber, stepCounter);
        logger.endKeyword(actionText);
    }
    
    /**
     * Build human-readable action text from expression.
     */
    private String buildActionText(Expression expr) {
        if (expr instanceof BinaryExpression) {
            BinaryExpression bin = (BinaryExpression) expr;
            String op = bin.getOperation().getText();
            
            if (op.equals("=")) {
                // Assignment: varName = value
                String left = bin.getLeftExpression().getText();
                String right = simplifyExpression(bin.getRightExpression());
                return left + " = " + right;
            }
        } else if (expr instanceof MethodCallExpression) {
            MethodCallExpression methodCall = (MethodCallExpression) expr;
            String method = methodCall.getMethodAsString();
            String args = buildArgumentsText(methodCall.getArguments());
            
            if (methodCall.isImplicitThis()) {
                return method + "(" + args + ")";
            } else {
                String object = methodCall.getObjectExpression().getText();
                // Drop the owner when it looks like an (imported) class name or a
                // class-alias import, so we log e.g. `runFeatureFileWithTags(...)`
                // instead of `CucumberKW.runFeatureFileWithTags(...)` to match
                // Katalon Studio's execution0.log format.
                if (isLikelyClassReference(object)) {
                    return method + "(" + args + ")";
                }
                return object + "." + method + "(" + args + ")";
            }
        } else if (expr instanceof StaticMethodCallExpression) {
            StaticMethodCallExpression call = (StaticMethodCallExpression) expr;
            String args = buildArgumentsText(call.getArguments());
            return call.getMethod() + "(" + args + ")";
        } else if (expr instanceof DeclarationExpression) {
            DeclarationExpression decl = (DeclarationExpression) expr;
            String left = decl.getLeftExpression().getText();
            String right = simplifyExpression(decl.getRightExpression());
            return left + " = " + right;
        }
        
        return expr.getText();
    }
    
    /**
     * Build arguments text for method call.
     */
    private String buildArgumentsText(Expression argsExpr) {
        if (argsExpr instanceof ArgumentListExpression) {
            ArgumentListExpression argList = (ArgumentListExpression) argsExpr;
            List<String> args = new ArrayList<>();
            for (Expression arg : argList.getExpressions()) {
                args.add(simplifyExpression(arg));
            }
            return String.join(", ", args);
        } else if (argsExpr instanceof TupleExpression) {
            TupleExpression tuple = (TupleExpression) argsExpr;
            List<String> args = new ArrayList<>();
            for (Expression arg : tuple.getExpressions()) {
                args.add(simplifyExpression(arg));
            }
            return String.join(", ", args);
        }
        return simplifyExpression(argsExpr);
    }
    
    /**
     * Heuristic: does an identifier look like a class reference (direct name
     * or import alias)?  Katalon's log output drops the alias prefix for static
     * keyword calls (e.g. `CucumberKW.runFeatureFile(...)` appears as
     * `runFeatureFile(...)`).  We approximate this without an import table by
     * treating any single dotted identifier whose first character is uppercase
     * as a class reference.
     */
    private boolean isLikelyClassReference(String text) {
        if (text == null || text.isEmpty()) return false;
        // Must be a bare identifier (or dotted FQN) starting with uppercase
        if (!Character.isUpperCase(text.charAt(0))) return false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (!Character.isLetterOrDigit(c) && c != '_' && c != '.' && c != '$') return false;
        }
        return true;
    }

    /**
     * Simplify expression for display (handle quotes, truncate long strings).
     */
    private String simplifyExpression(Expression expr) {
        if (expr instanceof ConstantExpression) {
            ConstantExpression constant = (ConstantExpression) expr;
            Object value = constant.getValue();
            if (value instanceof String) {
                String str = (String) value;
                // Katalon's execution0.log preserves full string arguments.
                return "&quot;" + escapeXml(str) + "&quot;";
            }
            return String.valueOf(value);
        } else if (expr instanceof VariableExpression) {
            return expr.getText();
        } else if (expr instanceof GStringExpression) {
            // Interpolated string
            return expr.getText();
        } else if (expr instanceof MapExpression) {
            return "[:]"; // Simplified map
        } else if (expr instanceof ListExpression) {
            return "[]"; // Simplified list
        } else if (expr instanceof ClosureExpression) {
            return "{ ... }"; // Closure abbreviated
        }
        
        String text = expr.getText();
        if (text.length() > 80) {
            return text.substring(0, 77) + "...";
        }
        return text;
    }
    
    /**
     * Escape XML special characters.
     */
    private String escapeXml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                   .replace("<", "&lt;")
                   .replace(">", "&gt;")
                   .replace("\"", "&quot;")
                   .replace("'", "&apos;");
    }
    
    /**
     * Strip imports that don't exist in katalan runtime
     */
    private String stripProblematicImports(String source) {
        String[] problematicPatterns = {
            "com.kms.katalon.core.testng.keyword.TestNGBuiltinKeywords",
            "com.kms.katalon.core.mobile.keyword.MobileBuiltInKeywords",
            "com.kms.katalon.core.cucumber.keyword.CucumberBuiltinKeywords",
            "com.kms.katalon.core.windows.keyword.WindowsBuiltinKeywords",
            "com.kms.katalon.core.testdata.TestDataFactory",
            "com.kms.katalon.core.checkpoint.CheckpointFactory",
            "com.kms.katalon.core.testcase.TestCaseFactory",
            "com.kms.katalon.core.testobject.ObjectRepository"
        };
        
        for (String pattern : problematicPatterns) {
            // Comment out import lines containing these patterns
            source = source.replaceAll("(?m)^(\\s*)import\\s+" + java.util.regex.Pattern.quote(pattern) + ".*$", 
                                      "$1// $0 // katalan: stripped");
        }
        
        return source;
    }
    
    /**
     * Skip actions that are too generic or internal.
     */
    private boolean shouldSkipAction(String actionText) {
        if (actionText == null || actionText.trim().isEmpty()) return true;
        
        // Skip simple variable references without assignment
        if (actionText.matches("^[a-zA-Z_][a-zA-Z0-9_]*$")) return true;
        
        // Skip internal Groovy constructs
        if (actionText.startsWith("$")) return true;
        if (actionText.startsWith("this.")) return true;
        
        return false;
    }
}
