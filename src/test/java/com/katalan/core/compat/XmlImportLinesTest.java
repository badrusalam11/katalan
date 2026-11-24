package com.katalan.core.compat;

import groovy.lang.GroovyClassLoader;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class XmlImportLinesTest {

    @Test
    void injectedImportsAddNoLines() {
        String withPkg = "package kw\n\nclass A {\n  def x() { new XmlSlurper() }\n}\n";
        String withPkgSemi = "package kw;\nclass A { def x() { new XmlSlurper() } }\n";
        String noPkg = "class A {\n  def x() { new XmlParser() }\n}\n";
        for (String src : new String[] {withPkg, withPkgSemi, noPkg}) {
            String out = GroovySourcePreprocessor.preprocess(src);
            assertEquals(src.split("\n", -1).length, out.split("\n", -1).length, out);
            new GroovyClassLoader().parseClass(out, "A.groovy"); // still compiles
        }
    }
}
