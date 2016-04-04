package nl.knaw.huygens.timbuctoo.search.description;

import com.google.common.collect.Maps;
import nl.knaw.huygens.timbuctoo.search.EntityRef;
import nl.knaw.huygens.timbuctoo.search.SearchResult;
import nl.knaw.huygens.timbuctoo.search.description.facet.FacetDescriptionFactory;
import nl.knaw.huygens.timbuctoo.search.description.property.PropertyDescriptorFactory;
import nl.knaw.huygens.timbuctoo.server.GraphWrapper;
import org.apache.tinkerpop.gremlin.process.traversal.P;
import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversal;
import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversalSource;
import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.__;
import org.apache.tinkerpop.gremlin.structure.Vertex;
import org.apache.tinkerpop.gremlin.structure.util.empty.EmptyGraph;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;

public class ReceptionSearchDescription extends WwDocumentSearchDescription {
  private final SearchResult otherSearch;
  public static final Logger LOG = LoggerFactory.getLogger(ReceptionSearchDescription.class);

  private static final String[] DOCUMENT_RELATIONS = {
    "hasEdition",
    "hasSequel",
    "hasTranslation",
    "hasAdaptation",
    "hasPlagiarismBy",
    "isAnnotatedIn",
    "hasBibliography",
    "isCensoredBy",
    "isWorkCommentedOnIn",
    "containedInAnthology",
    "isCopiedBy",
    "isWorkAwarded",
    "hasPreface",
    "isIntertextualOf",
    "isWorkListedOn",
    "isWorkMentionedIn",
    "isParodiedBy",
    "isWorkQuotedIn",
    "isWorkReferencedIn",
    "hasDocumentSource"
  };

  private static final String[] PERSON_RELATIONS = {
    "hasBiography",
    "isPersonCommentedOnIn",
    "isDedicatedPersonOf",
    "isPersonAwarded",
    "isPersonListedOn",
    "isPersonMentionedIn",
    "hasObituary",
    "isPersonQuotedIn",
    "isPersonReferencedIn"
  };



  public ReceptionSearchDescription(PropertyDescriptorFactory propertyDescriptorFactory,
                                    FacetDescriptionFactory facetDescriptionFactory, SearchResult otherSearch) {
    super(propertyDescriptorFactory, facetDescriptionFactory);
    this.otherSearch = otherSearch;
  }

  private String[] getRelationNames() {
    if (otherSearch.getSearchDescription().getType().equals("wwperson")) {
      return PERSON_RELATIONS;
    } else {
      return DOCUMENT_RELATIONS;
    }

  }

  @Override
  protected GraphTraversal<Vertex, Vertex> initializeVertices(GraphWrapper graphWrapper) {
    List<Vertex> otherResults = otherSearch.getSearchResult();

    LOG.info("Other search has type: {}", otherSearch.getSearchDescription().getType());
    if (otherResults == null) {
      return EmptyGraph.instance().traversal().V();
    }

    GraphTraversalSource latestStage = graphWrapper.getLatestState();
    GraphTraversal<Vertex, Vertex> vertices = filterByType(latestStage);

    return vertices.where(__.inE(getRelationNames()).otherV().is(P.within(otherResults)));
  }
}
