package nl.knaw.huygens.timbuctoo.rest.resources;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.GenericType;
import nl.knaw.huygens.timbuctoo.config.Paths;
import nl.knaw.huygens.timbuctoo.config.TypeNames;
import nl.knaw.huygens.timbuctoo.rest.util.AutocompleteResultConverter;
import nl.knaw.huygens.timbuctoo.rest.util.CustomHeaders;
import nl.knaw.huygens.timbuctoo.vre.VRE;
import org.junit.Test;
import test.rest.model.projecta.ProjectADomainEntity;

import javax.ws.rs.core.Response.Status;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class AutoCompleteResourceTest extends WebServiceTestSetup {
  protected static final Class<ProjectADomainEntity> DEFAULT_TYPE = ProjectADomainEntity.class;
  protected static final String DEFAULT_COLLECTION = TypeNames.getExternalName(DEFAULT_TYPE);
  public static final String QUERY = "query";
  public static final String SEARCH_PARAM = "test";
  public static final String KEY_KEY = "key";
  public static final String VALUE_KEY = "value";
  public static final String KEY_VALUE1 = "keyValue";
  public static final String VALUE_VALUE1 = "valueValue";
  public static final String KEY_VALUE2 = "keyValue";
  public static final String VALUE_VALUE2 = "valueValue";
  public static final String UNKNOWN_COLLECTION = "unknownCollections";

  @Test
  public void getLetsTheAutoCompleteResultProcessorProcessARawSearchResult() {
    // setup
    VRE vre = mock(VRE.class);
    makeVREAvailable(vre, VRE_ID);

    List<Map<String, Object>> rawSearchResult = Lists.<Map<String, Object>>newArrayList();

    when(vre.doRawSearch(DEFAULT_TYPE, SEARCH_PARAM)).thenReturn(rawSearchResult);
    AutocompleteResultConverter resultConverter = injector.getInstance(AutocompleteResultConverter.class);

    ArrayList<Map<String, Object>> convertedResult = Lists.<Map<String, Object>>newArrayList();
    convertedResult.add(createEntry(KEY_VALUE1, VALUE_VALUE1));
    convertedResult.add(createEntry(KEY_VALUE2, VALUE_VALUE2));
    when(resultConverter.convert(rawSearchResult)).thenReturn(convertedResult);

    // action
    ClientResponse response = resource().path(Paths.V2_PATH).path(Paths.DOMAIN_PREFIX).path(DEFAULT_COLLECTION).path(Paths.AUTOCOMPLETE_PATH)//
        .queryParam(QUERY, SEARCH_PARAM).header(CustomHeaders.VRE_ID_KEY, VRE_ID).get(ClientResponse.class);

    // verify
    responseStatusIs(response, Status.OK);

    List<Map<String, Object>> entity = response.getEntity(new GenericType<List<Map<String, Object>>>() {
    });

    assertThat(entity, hasSize(2));
    verifyEntry(entity.get(0), KEY_VALUE1, VALUE_VALUE1);
    verifyEntry(entity.get(1), KEY_VALUE2, VALUE_VALUE2);


  }

  protected HashMap<String, Object> createEntry(String key, String value) {
    HashMap<String, Object> entry = Maps.newHashMap();
    entry.put(KEY_KEY, key);
    entry.put(VALUE_KEY, value);
    return entry;
  }

  protected void verifyEntry(Map<String, Object> entry, String key, String value) {
    assertThat(entry.keySet(), containsInAnyOrder(KEY_KEY, VALUE_KEY));
    assertThat(valueAsString(entry, KEY_KEY), is(key));
    assertThat(valueAsString(entry, VALUE_KEY), is(value));
  }

  private String valueAsString(Map<String, Object> entry, String key) {
    return ""+ entry.get(key);
  }

  private void responseStatusIs(ClientResponse response, Status status) {
    assertThat(response.getStatusInfo().getStatusCode(), is(status.getStatusCode()));
  }

  @Test
  public void getReturnsNOT_FOUNDWhenNoResultsAreFound() {
    // setup
    VRE vre = mock(VRE.class);
    makeVREAvailable(vre, VRE_ID);

    // action
    ClientResponse response = resource().path(Paths.V2_PATH).path(Paths.DOMAIN_PREFIX).path(UNKNOWN_COLLECTION).path(Paths.AUTOCOMPLETE_PATH)//
        .queryParam(QUERY, SEARCH_PARAM).get(ClientResponse.class);

    // verify
    responseStatusIs(response, Status.NOT_FOUND);
  }

}
