package nl.knaw.huygens.repository.search;

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;

import nl.knaw.huygens.repository.config.DocTypeRegistry;
import nl.knaw.huygens.repository.facet.FacetCount;
import nl.knaw.huygens.repository.index.LocalSolrServer;
import nl.knaw.huygens.repository.model.Document;
import nl.knaw.huygens.repository.model.SearchResult;
import nl.knaw.huygens.solr.FacetInfo;
import nl.knaw.huygens.solr.FacetParameter;
import nl.knaw.huygens.solr.FacetedSearchParameters;
import nl.knaw.huygens.solr.SolrUtils;

import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.response.FacetField;
import org.apache.solr.client.solrj.response.FacetField.Count;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.SolrDocumentList;

import com.google.common.collect.Lists;
import com.google.inject.Inject;
import com.google.inject.Singleton;

@Singleton
public class SearchManager {

  private final LocalSolrServer server;
  private final FacetFinder facetFinder;
  private final AbstractFieldFinder fullTextSearchFieldFinder;
  private final DocTypeRegistry docTypeRegistry;
  private final SortableFieldFinder sortableFieldFinder;

  @Inject
  public SearchManager(LocalSolrServer server, DocTypeRegistry docTypeRegistry) {
    this.server = server;
    this.facetFinder = new FacetFinder();
    this.fullTextSearchFieldFinder = new FullTextSearchFieldFinder();
    this.docTypeRegistry = docTypeRegistry;
    this.sortableFieldFinder = new SortableFieldFinder();
  }

  public Set<String> findSortableFields(Class<? extends Document> type) {
    return sortableFieldFinder.findFields(type);
  }

  public SearchResult search(Class<? extends Document> type, String core, FacetedSearchParameters searchParameters) throws SolrServerException, FacetDoesNotExistException {
    Map<String, FacetInfo> facetInfoMap = facetFinder.findFacets(type);
    Set<String> fullTextSearchFields = fullTextSearchFieldFinder.findFields(type);
    String searchTerm = createSearchTerm(type, searchParameters, facetInfoMap.keySet(), fullTextSearchFields);
    QueryResponse response = server.search(core, searchTerm, facetInfoMap.keySet(), searchParameters.getSort());
    SolrDocumentList documents = response.getResults();

    List<FacetCount> facets = getFacetCounts(response.getFacetFields(), facetInfoMap);

    List<String> ids = Lists.newArrayList();
    for (SolrDocument document : documents) {
      ids.add(document.getFieldValue("id").toString());
    }

    SearchResult searchResult = new SearchResult(ids, docTypeRegistry.getINameForType(type), searchTerm, searchParameters.getSort(), new Date());
    searchResult.setFacets(facets);

    return searchResult;
  }

  private String createSearchTerm(Class<? extends Document> type, FacetedSearchParameters searchParameters, Set<String> existingFacets, Set<String> fullTextSearchFields)
      throws FacetDoesNotExistException {
    List<FacetParameter> facetValues = searchParameters.getFacetValues();
    boolean usesFacets = facetValues != null && !facetValues.isEmpty();
    StringBuilder builder = new StringBuilder();
    boolean isFirst = true;
    for (String fullTextSearchField : fullTextSearchFields) {
      if (!isFirst) {
        builder.append(" ");
      }
      builder.append(String.format("%s:%s", formatTextField(usesFacets, fullTextSearchField), formatTerm(searchParameters.getTerm())));
      isFirst = false;
    }
    if (usesFacets) {
      for (FacetParameter facetParameter : facetValues) {
        if (!existingFacets.contains(facetParameter.getName())) {
          throw new FacetDoesNotExistException("Facet " + facetParameter.getName() + " does not exist.");
        }
        builder.append(String.format(" +%s:%s", facetParameter.getName(), formatFacetValues(facetParameter.getValues())));
      }
    }

    return builder.toString();
  }

  private String formatFacetValues(List<String> values) {
    if (values.size() > 1) {
      StringBuilder builder = new StringBuilder();
      builder.append("(");
      String prefix = "";
      for (String value : values) {
        builder.append(prefix).append(SolrUtils.escapeFacetId(value));
        prefix = " ";
      }
      builder.append(")");
      return builder.toString();
    }
    return SolrUtils.escapeFacetId(values.get(0));
  }

  private String formatTextField(boolean usesFacets, String textField) {
    return String.format(usesFacets ? "+%s" : "%s", textField);
  }

  private String formatTerm(String term) {
    if (term.trim().contains(" ")) {
      return String.format("(%s)", term);
    }
    return term;
  }

  private List<FacetCount> getFacetCounts(List<FacetField> facetFields, Map<String, FacetInfo> facetInfoMap) {
    List<FacetCount> facets = Lists.newArrayList();
    for (FacetField facetField : facetFields) {
      FacetInfo info = facetInfoMap.get(facetField.getName());

      FacetCount facet = new FacetCount();
      facet.setName(facetField.getName());
      facet.setTitle(info.getTitle());
      facet.setType(info.getType());

      for (Count count : facetField.getValues()) {
        if (count.getCount() > 0) {
          facet.addOption(new FacetCount.Option().setName(count.getName()).setCount(count.getCount()));
        }
      }

      if (!facet.getOptions().isEmpty()) {
        facets.add(facet);
      }
    }

    return facets;
  }

}
