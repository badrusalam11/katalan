package groovy.util;

/**
 * Compatibility shim for Groovy 2.x/3.x scripts.
 *
 * <p>In Groovy 2.x/3.x, {@code XmlSlurper} lived in the {@code groovy.util}
 * package and was a default import, so scripts could write
 * {@code new XmlSlurper()} without any import. In Groovy 4.x it was moved
 * to {@code groovy.xml.XmlSlurper} and is no longer a default import.</p>
 *
 * <p>A large body of Katalon-era Groovy code (including dynamically
 * generated scripts evaluated via {@code Eval.me} / {@code new GroovyShell()})
 * still uses the unqualified form. We cannot preprocess those runtime-
 * generated sources, so instead we put an extending stub back where the
 * old class used to live. {@code groovy.util.*} is still a default import
 * in Groovy 4, so {@code new XmlSlurper()} resolves transparently.</p>
 */
public class XmlSlurper extends groovy.xml.XmlSlurper {

    public XmlSlurper() throws
            javax.xml.parsers.ParserConfigurationException, org.xml.sax.SAXException {
        super();
    }

    public XmlSlurper(org.xml.sax.XMLReader reader) {
        super(reader);
    }

    public XmlSlurper(javax.xml.parsers.SAXParser parser) throws org.xml.sax.SAXException {
        super(parser);
    }

    public XmlSlurper(boolean validating, boolean namespaceAware) throws
            javax.xml.parsers.ParserConfigurationException, org.xml.sax.SAXException {
        super(validating, namespaceAware);
    }

    public XmlSlurper(boolean validating, boolean namespaceAware, boolean allowDocTypeDeclaration) throws
            javax.xml.parsers.ParserConfigurationException, org.xml.sax.SAXException {
        super(validating, namespaceAware, allowDocTypeDeclaration);
    }
}
