package nl.knaw.huygens.timbuctoo.database.tinkerpop;

import nl.knaw.huygens.timbuctoo.database.dto.QuickSearch;
import nl.knaw.huygens.timbuctoo.database.dto.dataset.Collection;
import nl.knaw.huygens.timbuctoo.server.TinkerpopGraphManager;
import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversal;
import org.apache.tinkerpop.gremlin.structure.Vertex;
import org.junit.Before;
import org.junit.Test;
import org.neo4j.graphdb.Transaction;

import java.util.UUID;

import static nl.knaw.huygens.timbuctoo.util.TestGraphBuilder.newGraph;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class Neo4JIndexHandlerTest {

  public static final String COLLECTION = "collection";
  private Collection collection;

  @Before
  public void setUp() throws Exception {
    collection = mock(Collection.class);
    when(collection.getCollectionName()).thenReturn(COLLECTION);
  }

  @Test
  public void hasIndexForReturnsTrueIfTheIndexExists() {
    TinkerpopGraphManager tinkerpopGraphManager = newGraph().wrap();
    createIndexFor(tinkerpopGraphManager, collection);
    Neo4jIndexHandler instance = new Neo4jIndexHandler(tinkerpopGraphManager);

    boolean existingIndex = instance.hasIndexFor(collection);

    assertThat(existingIndex, is(true));
  }

  private void createIndexFor(TinkerpopGraphManager tinkerpopGraphManager, Collection collection) {
    Transaction tx = tinkerpopGraphManager.getGraphDatabase().beginTx();
    tinkerpopGraphManager.getGraphDatabase().index().forNodes(collection.getCollectionName());
    tx.close();
  }

  @Test
  public void hasIndexForReturnsFalseIfTheIndexDoesNotExist() {
    TinkerpopGraphManager tinkerpopGraphManager = newGraph().wrap();
    Neo4jIndexHandler instance = new Neo4jIndexHandler(tinkerpopGraphManager);

    Collection collectionWithoutIndex = mock(Collection.class);
    when(collectionWithoutIndex.getCollectionName()).thenReturn(COLLECTION);

    boolean existingIndex = instance.hasIndexFor(collectionWithoutIndex);

    assertThat(existingIndex, is(false));
  }

  @Test
  public void findByQuickSearchRetrievesTheVerticesFromTheIndexAndCreatesTraversalForThem() {
    String id1 = UUID.randomUUID().toString();
    String id2 = UUID.randomUUID().toString();
    String id3 = UUID.randomUUID().toString();
    TinkerpopGraphManager tinkerpopGraphManager = newGraph()
      .withVertex(v -> v
        .withTimId(id1)
        .withProperty("displayName", "query"))
      .withVertex(v -> v
        .withTimId(id2)
        .withProperty("displayName", "query2"))
      .withVertex(v -> v
        .withTimId(id3)
        .withProperty("displayName", "notmatching")
      )
      .wrap();
    Neo4jIndexHandler instance = new Neo4jIndexHandler(tinkerpopGraphManager);
    addToIndex(instance, collection, tinkerpopGraphManager.getGraph().traversal().V().has("tim_id", id1).next());
    addToIndex(instance, collection, tinkerpopGraphManager.getGraph().traversal().V().has("tim_id", id2).next());
    addToIndex(instance, collection, tinkerpopGraphManager.getGraph().traversal().V().has("tim_id", id3).next());
    QuickSearch quickSearch = QuickSearch.fromQueryString("query*");

    GraphTraversal<Vertex, Vertex> vertices = instance.findByQuickSearch(collection, quickSearch);

    assertThat(vertices.map(v -> v.get().value("tim_id")).toList(), containsInAnyOrder(id1, id2));
  }

  @Test
  public void findByQuickSearchIsCaseInsensitive() {
    String id1 = UUID.randomUUID().toString();
    String id2 = UUID.randomUUID().toString();
    String id3 = UUID.randomUUID().toString();
    TinkerpopGraphManager tinkerpopGraphManager = newGraph()
      .withVertex(v -> v
        .withTimId(id1)
        .withProperty("displayName", "query"))
      .withVertex(v -> v
        .withTimId(id2)
        .withProperty("displayName", "QUERY2"))
      .withVertex(v -> v
        .withTimId(id3)
        .withProperty("displayName", "notmatching")
      )
      .wrap();
    Neo4jIndexHandler instance = new Neo4jIndexHandler(tinkerpopGraphManager);
    addToIndex(instance, collection, tinkerpopGraphManager.getGraph().traversal().V().has("tim_id", id1).next());
    addToIndex(instance, collection, tinkerpopGraphManager.getGraph().traversal().V().has("tim_id", id2).next());
    addToIndex(instance, collection, tinkerpopGraphManager.getGraph().traversal().V().has("tim_id", id3).next());
    QuickSearch quickSearch = QuickSearch.fromQueryString("query*");

    GraphTraversal<Vertex, Vertex> vertices = instance.findByQuickSearch(collection, quickSearch);

    assertThat(vertices.map(v -> v.get().value("tim_id")).toList(), containsInAnyOrder(id1, id2));
  }

  @Test
  public void findByQuickSearchReturnsAnEmtptyTraversalWhenNoVerticesAreFound() {
    String id1 = UUID.randomUUID().toString();
    String id2 = UUID.randomUUID().toString();
    String id3 = UUID.randomUUID().toString();
    TinkerpopGraphManager tinkerpopGraphManager = newGraph()
      .withVertex(v -> v
        .withTimId(id1)
        .withProperty("displayName", "query"))
      .withVertex(v -> v
        .withTimId(id2)
        .withProperty("displayName", "query2"))
      .withVertex(v -> v
        .withTimId(id3)
        .withProperty("displayName", "other")
      )
      .wrap();
    Neo4jIndexHandler instance = new Neo4jIndexHandler(tinkerpopGraphManager);
    addToIndex(instance, collection, tinkerpopGraphManager.getGraph().traversal().V().has("tim_id", id1).next());
    addToIndex(instance, collection, tinkerpopGraphManager.getGraph().traversal().V().has("tim_id", id2).next());
    addToIndex(instance, collection, tinkerpopGraphManager.getGraph().traversal().V().has("tim_id", id3).next());
    QuickSearch quickSearch = QuickSearch.fromQueryString("queryWithoutResult");

    GraphTraversal<Vertex, Vertex> vertices = instance.findByQuickSearch(collection, quickSearch);

    assertThat(vertices.map(v -> v.get().value("tim_id")).toList(), is(empty()));
  }

  @Test
  public void findKeywordsByQuickSearchFiltersTheIndexResultsOnTheRightKeywordType() {
    String id1 = UUID.randomUUID().toString();
    String id2 = UUID.randomUUID().toString();
    String id3 = UUID.randomUUID().toString();
    TinkerpopGraphManager tinkerpopGraphManager = newGraph()
      .withVertex(v -> v
        .withTimId(id1)
        .withProperty("keyword_type", "keywordType")
        .withProperty("displayName", "query"))
      .withVertex(v -> v
        .withProperty("keyword_type", "otherType")
        .withTimId(id2)
        .withProperty("displayName", "query2"))
      .withVertex(v -> v
        .withProperty("keyword_type", "otherType")
        .withTimId(id3)
        .withProperty("displayName", "notmatching")
      )
      .wrap();
    Neo4jIndexHandler instance = new Neo4jIndexHandler(tinkerpopGraphManager);
    addToIndex(instance, collection, tinkerpopGraphManager.getGraph().traversal().V().has("tim_id", id1).next());
    addToIndex(instance, collection, tinkerpopGraphManager.getGraph().traversal().V().has("tim_id", id2).next());
    addToIndex(instance, collection, tinkerpopGraphManager.getGraph().traversal().V().has("tim_id", id3).next());
    QuickSearch quickSearch = QuickSearch.fromQueryString("query");

    GraphTraversal<Vertex, Vertex> vertices =
      instance.findKeywordsByQuickSearch(collection, quickSearch, "keywordType");

    assertThat(vertices.map(v -> v.get().value("tim_id")).toList(), containsInAnyOrder(id1));
  }

  @Test
  public void removeFromQuickSearchIndexRemovesTheVertexFromTheIndex() {
    String id1 = UUID.randomUUID().toString();
    TinkerpopGraphManager tinkerpopGraphManager = newGraph()
      .withVertex(v -> v
        .withTimId(id1)
      )
      .wrap();
    Neo4jIndexHandler instance = new Neo4jIndexHandler(tinkerpopGraphManager);
    Vertex vertex = tinkerpopGraphManager.getGraph().traversal().V().has("tim_id", id1).next();
    instance.addToOrUpdateQuickSearchIndex(collection, "query", vertex);
    GraphTraversal<Vertex, Vertex> beforeRemoval =
      instance.findByQuickSearch(collection, QuickSearch.fromQueryString("query"));
    assertThat(beforeRemoval.hasNext(), is(true));

    instance.removeFromQuickSearchIndex(collection, vertex);

    GraphTraversal<Vertex, Vertex> afterRemoval =
      instance.findByQuickSearch(collection, QuickSearch.fromQueryString("query"));
    assertThat(afterRemoval.hasNext(), is(false));
  }

  @Test
  public void addToOrUpdateQuickSearchIndexMakesSureTheExistingEntryIsUpdated() {
    String id1 = UUID.randomUUID().toString();
    TinkerpopGraphManager tinkerpopGraphManager = newGraph()
      .withVertex(v -> v
        .withTimId(id1)
      )
      .wrap();
    Neo4jIndexHandler instance = new Neo4jIndexHandler(tinkerpopGraphManager);
    Vertex vertex = tinkerpopGraphManager.getGraph().traversal().V().has("tim_id", id1).next();
    instance.addToOrUpdateQuickSearchIndex(collection, "firstValue", vertex);
    assertThat(instance.findByQuickSearch(collection, QuickSearch.fromQueryString("firstValue")).hasNext(), is(true));

    instance.addToOrUpdateQuickSearchIndex(collection, "secondValue", vertex);

    assertThat(instance.findByQuickSearch(collection, QuickSearch.fromQueryString("firstValue")).hasNext(), is(false));
    assertThat(instance.findByQuickSearch(collection, QuickSearch.fromQueryString("secondValue")).hasNext(), is(true));
  }

  private void addToIndex(Neo4jIndexHandler instance, Collection collection, Vertex vertex) {
    instance.addToQuickSearchIndex(collection, vertex.value("displayName"), vertex);
  }
}
