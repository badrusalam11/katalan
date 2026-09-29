package com.katalan.core.engine;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds the XPath Katalon uses for a test object whose {@code selectorMethod} is BASIC.
 *
 * In BASIC mode Katalon ignores {@code selectorCollection} and AND-combines the
 * selected ({@code isSelected=true}) {@code webElementProperties}: {@code tag} becomes
 * the element name, {@code text} matches the element's text, and every other property
 * is matched as an attribute using its {@code matchCondition}.
 */
public class BasicXPathBuilder {

    private final List<String> predicates = new ArrayList<>();
    private String tag = "*";

    /**
     * Add one selected property. Returns false (and adds nothing) for a match
     * condition that has no XPath 1.0 equivalent, so the caller can fall back.
     */
    public boolean add(String name, String matchCondition, String value) {
        if (name == null || value == null) {
            return true;
        }
        String condition = matchCondition == null ? "equals" : matchCondition.trim().toLowerCase();

        if ("tag".equalsIgnoreCase(name) && "equals".equals(condition)) {
            tag = value;
            return true;
        }

        String literal = literal(value);
        String predicate;
        if ("text".equalsIgnoreCase(name)) {
            predicate = textPredicate(condition, literal);
        } else {
            predicate = attributePredicate("@" + name, condition, literal);
        }
        if (predicate == null) {
            return false;
        }
        predicates.add(predicate);
        return true;
    }

    public boolean isEmpty() {
        return predicates.isEmpty() && "*".equals(tag);
    }

    public String build() {
        StringBuilder xpath = new StringBuilder("//").append(tag);
        if (!predicates.isEmpty()) {
            xpath.append('[').append(String.join(" and ", predicates)).append(']');
        }
        return xpath.toString();
    }

    private static String textPredicate(String condition, String literal) {
        switch (condition) {
            case "equals":
                return "(text() = " + literal + " or . = " + literal + ")";
            case "not equal":
                return "not(text() = " + literal + " or . = " + literal + ")";
            case "contains":
                return "(contains(text(), " + literal + ") or contains(., " + literal + "))";
            case "not contain":
                return "not(contains(text(), " + literal + ") or contains(., " + literal + "))";
            case "starts with":
                return "(starts-with(text(), " + literal + ") or starts-with(., " + literal + "))";
            case "ends with":
                return "(" + endsWith("text()", literal) + " or " + endsWith(".", literal) + ")";
            default:
                return null;
        }
    }

    private static String attributePredicate(String attr, String condition, String literal) {
        switch (condition) {
            case "equals":
                return attr + " = " + literal;
            case "not equal":
                return attr + " != " + literal;
            case "contains":
                return "contains(" + attr + ", " + literal + ")";
            case "not contain":
                return "not(contains(" + attr + ", " + literal + "))";
            case "starts with":
                return "starts-with(" + attr + ", " + literal + ")";
            case "ends with":
                return endsWith(attr, literal);
            default:
                return null;
        }
    }

    // XPath 1.0 (what browsers implement) has no ends-with()
    private static String endsWith(String expr, String literal) {
        return "substring(" + expr + ", string-length(" + expr + ") - string-length(" + literal + ") + 1) = " + literal;
    }

    /** Quote a value as an XPath string literal, using concat() when it holds both quote kinds. */
    static String literal(String value) {
        if (!value.contains("'")) {
            return "'" + value + "'";
        }
        if (!value.contains("\"")) {
            return "\"" + value + "\"";
        }
        StringBuilder sb = new StringBuilder("concat(");
        String[] parts = value.split("'", -1);
        for (int i = 0; i < parts.length; i++) {
            if (i > 0) {
                sb.append(", \"'\", ");
            }
            sb.append('\'').append(parts[i]).append('\'');
        }
        return sb.append(')').toString();
    }
}
