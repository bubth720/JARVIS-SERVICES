package fr.jarvis.services.modules.documents;

import fr.jarvis.services.shared.api.Contracts.Failure;
import java.io.ByteArrayInputStream;
import javax.xml.parsers.SAXParserFactory;
import org.xml.sax.*;
import org.xml.sax.ext.DefaultHandler2;

/**
 * Namespace-aware, bounded XML extraction with mandatory fail-closed DTD callbacks. No Xerces-only
 * features, no entity expansion, no external resources or XInclude processing.
 */
final class DocxXml extends DefaultHandler2 {
  private static final String WORD = "http://schemas.openxmlformats.org/wordprocessingml/2006/main";
  private final StringBuilder text = new StringBuilder();
  private int paragraphDepth;

  static String extract(byte[] bytes) throws Exception {
    SAXParserFactory factory = SAXParserFactory.newInstance();
    factory.setNamespaceAware(true);
    factory.setValidating(false);
    XMLReader reader = factory.newSAXParser().getXMLReader();
    DocxXml handler = new DocxXml();
    // Both JDK SAX and Android Expat implement this property. Failure is fatal, never ignored.
    reader.setProperty("http://xml.org/sax/properties/lexical-handler", handler);
    reader.setContentHandler(handler);
    reader.setEntityResolver(handler);
    reader.setErrorHandler(handler);
    reader.parse(new InputSource(new ByteArrayInputStream(bytes)));
    return handler.text.toString();
  }

  @Override
  public void startDTD(String name, String publicId, String systemId) throws SAXException {
    throw new SAXException("DTD_FORBIDDEN");
  }

  @Override
  public InputSource resolveEntity(String publicId, String systemId) throws SAXException {
    throw new SAXException("EXTERNAL_ENTITY_FORBIDDEN");
  }

  @Override
  public InputSource resolveEntity(String name, String publicId, String baseURI, String systemId)
      throws SAXException {
    throw new SAXException("EXTERNAL_ENTITY_FORBIDDEN");
  }

  @Override
  public void skippedEntity(String name) throws SAXException {
    throw new SAXException("ENTITY_FORBIDDEN");
  }

  @Override
  public void error(SAXParseException error) throws SAXException {
    throw error;
  }

  @Override
  public void fatalError(SAXParseException error) throws SAXException {
    throw error;
  }

  @Override
  public void startElement(String uri, String local, String qName, Attributes attrs) {
    if (WORD.equals(uri) && "p".equals(local)) paragraphDepth++;
  }

  @Override
  public void endElement(String uri, String local, String qName) {
    if (WORD.equals(uri) && "p".equals(local)) {
      paragraphDepth--;
      text.append('\n');
      bounded();
    }
  }

  @Override
  public void characters(char[] chars, int offset, int length) {
    if (paragraphDepth > 0) {
      text.append(chars, offset, length);
      bounded();
    }
  }

  private void bounded() {
    if (text.length() > DocumentContent.MAX_TEXT) throw new Failure("TEXT_TOO_LARGE");
  }
}
