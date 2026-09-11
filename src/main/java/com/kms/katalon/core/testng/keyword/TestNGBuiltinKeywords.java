package com.kms.katalon.core.testng.keyword;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Katalon compatibility stub for TestNGBuiltinKeywords.
 * Katalan does not support TestNG suite execution; this class exists purely
 * so that scripts' boilerplate `import ... TestNGBuiltinKeywords as TestNGKW`
 * resolves on every execution path (test cases, nested callTestCase, BDD
 * glue code), even when the keyword is never actually invoked.
 */
public class TestNGBuiltinKeywords {

    private static final Logger logger = LoggerFactory.getLogger(TestNGBuiltinKeywords.class);

    public static Object runFeatureFile(String featureFile) {
        logger.warn("TestNGKW.runFeatureFile({}) - not supported by Katalan", featureFile);
        return null;
    }

    public static Object runFeatureFolder(String featureFolder) {
        logger.warn("TestNGKW.runFeatureFolder({}) - not supported by Katalan", featureFolder);
        return null;
    }

    public static Object runWithTestNGRunner(Object... args) {
        logger.warn("TestNGKW.runWithTestNGRunner - not supported by Katalan");
        return null;
    }
}
