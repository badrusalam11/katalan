package groovy.util;

import java.io.PrintWriter;

/**
 * Compatibility shim for Groovy 2.x/3.x scripts that reference
 * {@code new XmlNodePrinter(...)} without an import. See {@link XmlSlurper}
 * for the full rationale.
 */
public class XmlNodePrinter extends groovy.xml.XmlNodePrinter {

    public XmlNodePrinter() {
        super();
    }

    public XmlNodePrinter(PrintWriter out) {
        super(out);
    }

    public XmlNodePrinter(PrintWriter out, String indent) {
        super(out, indent);
    }

    public XmlNodePrinter(PrintWriter out, String indent, String quote) {
        super(out, indent, quote);
    }

    public XmlNodePrinter(groovy.util.IndentPrinter out) {
        super(out);
    }
}
