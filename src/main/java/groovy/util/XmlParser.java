package groovy.util;

/**
 * Compatibility shim for Groovy 2.x/3.x scripts that reference
 * {@code new XmlParser()} without an import. See {@link XmlSlurper}
 * for the full rationale.
 */
public class XmlParser extends groovy.xml.XmlParser {

    public XmlParser() throws javax.xml.parsers.ParserConfigurationException, org.xml.sax.SAXException {
        super();
    }

    public XmlParser(boolean validating, boolean namespaceAware) throws
            javax.xml.parsers.ParserConfigurationException, org.xml.sax.SAXException {
        super(validating, namespaceAware);
    }

    public XmlParser(boolean validating, boolean namespaceAware, boolean allowDocTypeDeclaration) throws
            javax.xml.parsers.ParserConfigurationException, org.xml.sax.SAXException {
        super(validating, namespaceAware, allowDocTypeDeclaration);
    }

    public XmlParser(org.xml.sax.XMLReader reader) {
        super(reader);
    }

    public XmlParser(javax.xml.parsers.SAXParser parser) throws org.xml.sax.SAXException {
        super(parser);
    }
}
