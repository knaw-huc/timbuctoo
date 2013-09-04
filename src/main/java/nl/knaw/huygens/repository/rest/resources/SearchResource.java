package nl.knaw.huygens.repository.rest.resources;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.ws.rs.Consumes;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.ResponseBuilder;
import javax.ws.rs.core.UriBuilder;
import javax.ws.rs.core.UriInfo;

import nl.knaw.huygens.repository.annotations.APIDesc;
import nl.knaw.huygens.repository.config.DocTypeRegistry;
import nl.knaw.huygens.repository.model.Document;
import nl.knaw.huygens.repository.model.SearchResult;
import nl.knaw.huygens.repository.search.FacetDoesNotExistException;
import nl.knaw.huygens.repository.search.SearchManager;
import nl.knaw.huygens.repository.storage.JsonViews;
import nl.knaw.huygens.repository.storage.StorageManager;
import nl.knaw.huygens.solr.FacetedSearchParameters;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.annotation.JsonView;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.inject.Inject;

@Path("search")
public class SearchResource {

  private static final Logger LOG = LoggerFactory.getLogger(SearchResource.class);

  @Inject
  private SearchManager searchManager;
  @Inject
  private StorageManager storageManager;
  @Inject
  private DocTypeRegistry registry;

  @POST
  @APIDesc("Searches the Solr index")
  @Consumes(MediaType.APPLICATION_JSON)
  public Response post(FacetedSearchParameters searchParameters) {
    String typeString = searchParameters.getTypeString();
    String q = searchParameters.getTerm();

    // Validate input
    if (typeString == null || q == null) {
      LOG.error("POST - type: '{}', q: '{}'", typeString, q);
      throw new WebApplicationException(Response.Status.BAD_REQUEST);
    }
    Class<? extends Document> type = registry.getTypeForIName(typeString);
    if (type == null) {
      LOG.error("POST - no type {}", typeString);
      throw new WebApplicationException(Response.Status.NOT_FOUND);
    }

    // Process
    try {
      // FIX the `SearchResource shouldn't know the relation between types and cores
      Class<? extends Document> baseType = registry.getBaseClass(type);
      String core = registry.getINameForType(baseType);
      SearchResult result = searchManager.search(type, core, searchParameters);
      storageManager.addDocument(SearchResult.class, result);
      String queryId = result.getId();
      return Response.created(new URI(queryId)).build();
    } catch (FacetDoesNotExistException ex) {
      LOG.warn("POST - {}", ex.getMessage());
      throw new WebApplicationException(Response.Status.BAD_REQUEST);
    } catch (Exception e) {
      LOG.warn("POST - {}", e.getMessage());
      throw new WebApplicationException(Response.Status.INTERNAL_SERVER_ERROR);
    }
  }

  @GET
  @Path("/{id: QRY\\d+}")
  @APIDesc("Returns (paged) search results")
  @Produces({ MediaType.APPLICATION_JSON, MediaType.TEXT_HTML })
  @JsonView(JsonViews.WebView.class)
  public Response get( //
      @PathParam("id") String queryId, //
      @QueryParam("start") @DefaultValue("0") final int start, //
      @QueryParam("rows") @DefaultValue("10") final int rows, //
      @Context UriInfo uriInfo) {

    // Retrieve result
    SearchResult result = storageManager.getDocument(SearchResult.class, queryId);
    if (result == null) {
      LOG.error("GET - no results for id '{}'", queryId);
      throw new WebApplicationException(Response.Status.NOT_FOUND);
    }

    // Process
    Class<? extends Document> type = registry.getTypeForIName(result.getSearchType());
    if (type == null) {
      LOG.error("GET - no document type for '{}'", result.getSearchType());
      throw new WebApplicationException(Response.Status.BAD_REQUEST);
    }
    List<String> ids = result.getIds();
    int lo = toRange(start, 0, ids.size());
    int hi = toRange(lo + rows, 0, ids.size());

    List<String> idsToGet = ids.subList(lo, hi);
    Set<String> sortableFields = searchManager.findSortableFields(type);

    Map<String, Object> returnValue = Maps.newConcurrentMap();
    returnValue.put("term", result.getTerm());
    returnValue.put("facets", result.getFacets());
    returnValue.put("numFound", ids.size());
    returnValue.put("ids", idsToGet);
    returnValue.put("results", convert(type, ids, lo, hi));
    returnValue.put("start", lo);
    returnValue.put("rows", idsToGet.size());
    returnValue.put("sortableFields", sortableFields);

    System.out.println("path: " + uriInfo.getAbsolutePath());

    UriBuilder prevUriBuilder = uriInfo.getAbsolutePathBuilder();
    UriBuilder nextUriBuilder = uriInfo.getAbsolutePathBuilder();

    if (start > 0) {
      int prevStart = Math.max(start - rows, 0);
      prevUriBuilder.queryParam("start", prevStart).queryParam("rows", rows);
      returnValue.put("_prev", prevUriBuilder.build());
    }

    if (hi < ids.size()) {
      nextUriBuilder.queryParam("start", start + rows).queryParam("rows", rows);
      returnValue.put("_next", nextUriBuilder.build());
    }

    ResponseBuilder response = Response.ok(returnValue);

    return response.build();
  }

  private int toRange(int value, int minValue, int maxValue) {
    return Math.min(Math.max(value, minValue), maxValue);
  }

  private <T extends Document> List<T> convert(Class<T> type, List<String> ids, int lo, int hi) {
    List<T> list = Lists.newArrayList();
    // TODO get all at once
    for (int index = lo; index < hi; index++) {
      String id = ids.get(index);
      list.add(storageManager.getDocument(type, id));
    }
    return list;
  }

}
