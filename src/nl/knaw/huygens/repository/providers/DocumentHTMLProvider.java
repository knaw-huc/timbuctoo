package nl.knaw.huygens.repository.providers;

import java.io.IOException;
import java.io.OutputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;

import javax.ws.rs.Produces;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.ext.MessageBodyWriter;
import javax.ws.rs.ext.Provider;

import nl.knaw.huygens.repository.model.Document;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;

@Provider
@Produces(MediaType.TEXT_HTML)
@Singleton
public class DocumentHTMLProvider implements MessageBodyWriter<Document> {

  private final HTMLProviderHelper helper;

  @Inject
  public DocumentHTMLProvider(@Named("html.defaultstylesheet") String stylesheetLink, @Named("public_url") String publicURL) {
    helper = new HTMLProviderHelper(stylesheetLink, publicURL);
  }

  @Override
  public boolean isWriteable(Class<?> type, Type genericType, Annotation[] annotations, MediaType mediaType) {
    return helper.accept(mediaType) && accept(type);
  }

  private boolean accept(Class<?> type) {
    return Document.class.isAssignableFrom(type);
  }

  @Override
  public long getSize(Document doc, Class<?> type, Type genericType, Annotation[] annotations, MediaType mediaType) {
    return -1;
  }

  @Override
  public void writeTo(Document doc, Class<?> type, Type genericType, Annotation[] annotations, MediaType mediaType, MultivaluedMap<String, Object> httpHeaders, OutputStream out) throws IOException,
      WebApplicationException {
    helper.writeHeader(out, doc.getDescription());

    JsonGenerator jgen = helper.getGenerator(out);
    ObjectWriter writer = helper.getObjectWriter(annotations);
    writer.writeValue(jgen, doc);

    helper.writeFooter(out);
  }

}
