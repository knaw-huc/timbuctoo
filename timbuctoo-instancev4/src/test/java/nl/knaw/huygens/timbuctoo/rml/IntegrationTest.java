package nl.knaw.huygens.timbuctoo.rml;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.collect.ImmutableMap;
import io.dropwizard.testing.junit.DropwizardAppRule;
import nl.knaw.huygens.timbuctoo.server.TimbuctooConfiguration;
import nl.knaw.huygens.timbuctoo.server.TimbuctooV4;
import nl.knaw.huygens.timbuctoo.util.EvilEnvironmentVariableHacker;
import org.assertj.core.util.Lists;
import org.glassfish.jersey.client.ClientConfig;
import org.glassfish.jersey.media.multipart.FormDataBodyPart;
import org.glassfish.jersey.media.multipart.FormDataContentDisposition;
import org.glassfish.jersey.media.multipart.MultiPart;
import org.glassfish.jersey.media.multipart.MultiPartFeature;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.Invocation;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.text.ParseException;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import static com.google.common.io.Resources.asCharSource;
import static com.google.common.io.Resources.getResource;
import static io.dropwizard.testing.ResourceHelpers.resourceFilePath;
import static nl.knaw.huygens.timbuctoo.util.JsonBuilder.jsn;
import static nl.knaw.huygens.timbuctoo.util.JsonBuilder.jsnA;
import static nl.knaw.huygens.timbuctoo.util.JsonBuilder.jsnO;
import static nl.knaw.huygens.timbuctoo.util.JsonContractMatcher.matchesContract;
import static nl.knaw.huygens.timbuctoo.util.StreamIterator.stream;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.core.Is.is;

public class IntegrationTest {

  static {
    EvilEnvironmentVariableHacker.setEnv(ImmutableMap.of(
      "timbuctoo_dataPath", resourceFilePath("integrationtest"),
      "timbuctoo_port", "0",
      "timbuctoo_adminPort", "0"
    ));
  }

  @ClassRule
  public static final DropwizardAppRule<TimbuctooConfiguration> APP = new DropwizardAppRule<>(
    TimbuctooV4.class,
    "example_config.yaml"
  );


  private static final String AUTH = "FAKE_AUTH_TOKEN";
  private static Client client;

  @BeforeClass
  public static void beforeClass() throws IOException {
    // client = new JerseyClientBuilder(APP.getEnvironment()).build("test client");
    ClientConfig configuration = new ClientConfig();
    client = ClientBuilder.newClient(configuration);
  }

  @Test
  public void succeedingSmallUpload() throws Exception {
    String vreName = "demo-upload";
    String prefixedVreName = "DUMMY_" + vreName;
    Response uploadResponse = rawUpload(
      vreName,
      new File(getResource(IntegrationTest.class, "demo-upload.xlsx").toURI())
    );

    assertThat(uploadResponse.getStatus(), is(200));
    uploadResponse.readEntity(String.class); //needed for blocking side-effect

    Response rmlResponse = map(
      prefixedVreName,
      asCharSource(getResource(IntegrationTest.class, "demo-upload-rml.json"), Charset.forName("UTF8")).read()
        .replace("{VRE_NAME}", prefixedVreName)
    );

    String output = rmlResponse.readEntity(String.class); //also needed for blocking side-effect
    String rmlResult = output.substring(output.lastIndexOf('\n') + 1);
    assertThat(rmlResult, is("success"));

    JsonNode metadata = call("/v2.1/metadata/" + prefixedVreName + "?withCollectionInfo=true").get(JsonNode.class);
    String personsType = prefixedVreName + "Persons";
    String placesType = prefixedVreName + "Places";
    String unknownType = prefixedVreName + "unknown";
    String personsCollection = personsType + "s";
    String placesCollection = placesType + "s";

    assertThat(metadata, matchesContract(jsnO(
      personsCollection, jsnO(),
      placesCollection, jsnO()
    )));

    JsonNode persons = call("/v2.1/domain/" + personsCollection + "/autocomplete?query=helmhol*").get(JsonNode.class);
    assertThat("No person found", persons.has(0), is(true));
    JsonNode personData = call(persons.get(0).get("key").asText()).get(JsonNode.class);
    assertThat(personData, matchesContract(jsnO(
      "@displayName", jsn("H.L.F. Helmholtz"),
      "@relations", jsnO(
        "hasBirthPlace", jsnA(
            jsnO(
            "displayName", jsn("Potsdam")
          )
        )
      ),
      "@variationRefs", jsnO(
        "custom-matcher", jsn("/*ALL_MATCH_ONE_OF*/"),
        "keyProp", jsn("type"),
        "possibilities", jsnO(
          personsType, jsnO(
            "type", jsn(personsType)
          ),
          "person", jsnO(
            "type", jsn("person")
          ),
          "concept", jsnO(
            "type", jsn("concept")
          ),
          unknownType, jsnO(
            "type", jsn(unknownType)
          )
        )
      )
    )));
  }

  @Test
  public void personNameVariantAreAddedToThePersonTheyBelongTo() throws Exception {
    String vreName = "demo-upload";
    String prefixedVreName = "DUMMY_" + vreName;
    Response uploadResponse = rawUpload(
      vreName,
      new File(getResource(IntegrationTest.class, "BIA_klein_ok.xlsx").toURI())
    );

    assertThat("Successful upload of excel", uploadResponse.getStatus(), is(200));
    uploadResponse.readEntity(String.class); //needed for blocking side-effect

    Response rmlResponse = map(
      prefixedVreName,
      asCharSource(getResource(
        IntegrationTest.class,
        "alternative-names-rml.json"),
        Charset.forName("UTF8")).read().replace("{VRE_NAME}", prefixedVreName)
    );

    String output = rmlResponse.readEntity(String.class); //also needed for blocking side-effect
    String rmlResult = output.substring(output.lastIndexOf('\n') + 1);
    assertThat("Succesful rml execution", rmlResult, is("success"));

    JsonNode metadata = call("/v2.1/metadata/" + prefixedVreName + "?withCollectionInfo=true").get(JsonNode.class);
    String personsType = prefixedVreName + "Persons";
    String personsCollection = personsType + "s";

    assertThat(metadata, matchesContract(jsnO(
      personsCollection, jsnO()
    )));

    JsonNode personsNode = call("/v2.1/domain/" + personsCollection).get(JsonNode.class);

    List<JsonNode> persons = Lists.newArrayList(personsNode.iterator());
    assertThat(persons, hasSize(2));
    int personWithTwoNamesIndex;
    int personWithOneNameIndex;
    if (persons.get(0).get("names").size() == 2) {
      personWithTwoNamesIndex = 0;
      personWithOneNameIndex = 1;
    } else {
      personWithTwoNamesIndex = 1;
      personWithOneNameIndex = 0;
    }
    assertThat(personsNode.get(personWithTwoNamesIndex).get("names"), containsInAnyOrder(jsnO(
      "components", jsnA(
        jsnO(
          "type", jsn("FORENAME"),
          "value", jsn("Jacques Henrij")
        ),
        jsnO(
          "type", jsn("SURNAME"),
          "value", jsn("Abendanon")
        )
      )
      ),
      jsnO(
        "components", jsnA(
          jsnO(
            "type", jsn("FORENAME"),
            "value", jsn("Christiaen")
          ),
          jsnO(
            "type", jsn("SURNAME"),
            "value", jsn("Christiaensen")
          )
        )
      )
    ));


    assertThat(personsNode.get(personWithOneNameIndex).get("names"), is(jsnA(
      jsnO(
        "components", jsnA(
          jsnO(
            "type", jsn("FORENAME"),
            "value", jsn("Miguel")
          ),
          jsnO(
            "type", jsn("SURNAME"),
            "value", jsn("Asin y Palacios")
          )
        )
      )
      )
    ));
  }


  @Test
  public void succeedingRdfUploadWithGraphql() throws Exception {
    String vreName = "clusius-" + UUID.randomUUID();
    Response uploadResponse = multipartPost(
      "/v5/DUMMY/" + vreName + "/upload/rdf",
      new File(getResource(IntegrationTest.class, "bia_clusius.ttl").toURI()),
      "text/turtle",
      ImmutableMap.of(
        "encoding", "UTF-8",
        "uri", "http://example.com/clusius"
      )
    );

    assertThat("Successful upload of rdf", uploadResponse.getStatus(), is(204));
    uploadResponse.readEntity(String.class);

    Response graphqlCall = call("/v5/DUMMY/" + vreName + "/graphql")
      .accept(MediaType.APPLICATION_JSON)
      .post(Entity.entity("{\n" +
        "  http___timbuctoo_huygens_knaw_nl_datasets_clusius_ResidenceList {\n" +
        "    items {\n" +
        "      uri\n" +
        "    }\n" +
        "  }\n" +
        "}", MediaType.valueOf("application/graphql")));
    ObjectNode objectNode = graphqlCall.readEntity(ObjectNode.class);
    assertThat(objectNode
      .get("data")
      .get("http___timbuctoo_huygens_knaw_nl_datasets_clusius_ResidenceList")
      .get("items").size(),
      is(20)
    );

    graphqlCall = call("/v5/DUMMY/" + vreName + "/graphql")
      .accept(MediaType.APPLICATION_JSON)
      .post(Entity.entity("{\n" +
        "  http___timbuctoo_huygens_knaw_nl_datasets_clusius_ResidenceList {\n" +
        "    items {\n" +
        "      http___timbuctoo_huygens_knaw_nl_properties_hasLocation {\n" +
        "        http___timbuctoo_huygens_knaw_nl_properties_name {\n" +
        "          value\n" +
        "        }\n" +
        "      }\n" +
        "    }\n" +
        "  }\n" +
        "}", MediaType.valueOf("application/graphql")));
    objectNode = graphqlCall.readEntity(ObjectNode.class);
    assertThat(
      stream(objectNode
        .get("data")
        .get("http___timbuctoo_huygens_knaw_nl_datasets_clusius_ResidenceList")
        .get("items").iterator())
        .map(item -> item
          .get("http___timbuctoo_huygens_knaw_nl_properties_hasLocation")
          .get("http___timbuctoo_huygens_knaw_nl_properties_name")
          .get("value"))
        .filter(Objects::nonNull)
        .count(),
      is(20L)
    );
  }


  private Response rawUpload(String vrename, File resource) throws ParseException {
    Map<String, String> arguments = ImmutableMap.of(
      "vreName", vrename
    );
    String path = "/v2.1/bulk-upload";

    return multipartPost(
      path,
      resource,
      "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
      arguments
    );
  }

  private Response multipartPost(String path, File resource, String mediaType, Map<String, String> arguments)
    throws ParseException {
    MultiPart formdata = new MultiPart();
    arguments.forEach((key, value) -> formdata.bodyPart(new FormDataBodyPart(key, value)));

    formdata.bodyPart(new FormDataBodyPart(
      new FormDataContentDisposition(
        "form-data; name=\"file\"; filename=\"" + resource.getName().replace("\"", "") + "\""
      ),
      resource,
      MediaType.valueOf(mediaType)
    ));

    Response result = call(path)
      .post(Entity.entity(formdata, "multipart/form-data; boundary=Boundary_1_498293219_1483974344746"));
    return result;
  }

  private Response map(String vrename, String rmlMapping) {
    Response response = call("/v2.1/bulk-upload/" + vrename + "/rml/execute")
      .post(Entity.entity(rmlMapping, new MediaType("application", "ld+json")));
    return response;
  }

  private Invocation.Builder call(String path) {
    String server;
    if (path.startsWith("http://") || path.startsWith("https://")) {
      server = path;
    } else {
      int localPort = APP.getLocalPort();
      // int localPort = 8080;
      server = String.format("http://localhost:%d" + path, localPort);
    }
    return client
      .target(server)
      .register(MultiPartFeature.class)
      // .register(new LoggingFilter(Logger.getLogger(LoggingFilter.class.getName()), true))
      .request()
      .header("Authorization", AUTH);
  }
}
