package nl.knaw.huygens.repository.providers;

import java.io.ByteArrayOutputStream;

import javax.ws.rs.core.MediaType;

import nl.knaw.huygens.repository.model.Document;
import nl.knaw.huygens.repository.model.User;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class DocumentHTMLProviderTest {

  private DocumentHTMLProvider provider;

  @Before
  public void setup() {
    provider = new DocumentHTMLProvider("link", "url");
  }

  private void assertIsWritable(boolean expected, Class<?> type, MediaType mediaType) {
    Assert.assertEquals(expected, provider.isWriteable(type, null, type.getAnnotations(), mediaType));
  }

  @Test
  public void documentIsWritable() {
    assertIsWritable(true, Document.class, MediaType.TEXT_HTML_TYPE);
    assertIsWritable(false, Document.class, MediaType.APPLICATION_JSON_TYPE);
  }

  @Test
  public void subClassIsWritable() {
    assertIsWritable(true, User.class, MediaType.TEXT_HTML_TYPE);
    assertIsWritable(false, User.class, MediaType.APPLICATION_JSON_TYPE);
  }

  @Test
  public void otherClassIsWritable() {
    assertIsWritable(false, DocumentHTMLProvider.class, MediaType.TEXT_HTML_TYPE);
    assertIsWritable(false, DocumentHTMLProvider.class, MediaType.APPLICATION_JSON_TYPE);
  }

  @Test
  public void testWriteTo() throws Exception {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    provider.writeTo(new User(), User.class, null, User.class.getAnnotations(), MediaType.TEXT_HTML_TYPE, null, out);
    String value = out.toString();
    Assert.assertTrue(value.startsWith("<!DOCTYPE html><html><head>"));
    Assert.assertTrue(value.endsWith("</body></html>"));
  }

}
