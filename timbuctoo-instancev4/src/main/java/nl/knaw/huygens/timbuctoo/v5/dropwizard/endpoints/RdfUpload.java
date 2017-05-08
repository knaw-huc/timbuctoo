package nl.knaw.huygens.timbuctoo.v5.dropwizard.endpoints;

import nl.knaw.huygens.timbuctoo.v5.datastores.dto.StoreStatus;
import nl.knaw.huygens.timbuctoo.v5.logprocessing.ImportManager;
import nl.knaw.huygens.timbuctoo.v5.logprocessing.exceptions.LogProcessingFailedException;
import nl.knaw.huygens.timbuctoo.v5.logprocessing.exceptions.LogStorageFailedException;
import org.glassfish.jersey.media.multipart.FormDataBodyPart;
import org.glassfish.jersey.media.multipart.FormDataParam;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.Charset;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

@Path("/v4/rdf-upload/{dataSet}")
public class RdfUpload {

  protected final ImportManager importManager;

  public RdfUpload(ImportManager importManager) {
    this.importManager = importManager;
  }

  /*
  curl \
  -F "uri=http://timbuctoo.com/clusius.nt" \
  -F "encoding=UTF-8" \
  -F file=@/Users/jauco/Dropbox\ \(Huygens-ICT\)/Team-red/test_sets/bia_clusius.nt;type=application/n-triples \
  http://localhost:8080/v4/rdf-upload/clusius/

  curl http://localhost:8080/v4/rdf-upload/clusius/
   */

  @Consumes(MediaType.MULTIPART_FORM_DATA)
  @POST
  public void upload(@FormDataParam("file") final InputStream rdfInputStream,
                     @FormDataParam("file") final FormDataBodyPart body,
                     @FormDataParam("encoding") final String encoding,
                     @FormDataParam("uri") final URI uri,
                     @PathParam("dataSet") final String dataSetId)
      throws IOException, LogProcessingFailedException, LogStorageFailedException, ExecutionException,
      InterruptedException {
    Future<?> promise = importManager.addLog(
      dataSetId,
      uri,
      body == null || body.getMediaType() == null ?
        Optional.empty() :
        Optional.of(body.getMediaType().toString()),
      Optional.of(Charset.forName(encoding)),
      rdfInputStream
    );
    promise.get();
  }

  @GET
  @Produces(MediaType.APPLICATION_JSON)
  public Map<String, StoreStatus> upload(@PathParam("dataSet") final String dataSetId) throws IOException {
    return importManager.getStatus(dataSetId);
  }

}
