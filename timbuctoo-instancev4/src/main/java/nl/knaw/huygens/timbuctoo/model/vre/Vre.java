package nl.knaw.huygens.timbuctoo.model.vre;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversal;
import org.apache.tinkerpop.gremlin.structure.Graph;
import org.apache.tinkerpop.gremlin.structure.Vertex;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public class Vre {
  private static final Logger LOG = LoggerFactory.getLogger(Vre.class);
  public static final String HAS_COLLECTION_RELATION_NAME = "hasCollection";
  public static final String VRE_NAME_PROPERTY_NAME = "name";
  public static final String KEYWORD_TYPES_PROPERTY_NAME = "keywordTypes";
  public static final String DATABASE_LABEL = "VRE";

  private final String vreName;
  private final LinkedHashMap<String, Collection> collections = Maps.newLinkedHashMap();

  Vre(String vreName) {
    this.vreName = vreName;
  }

  public Collection getCollectionForTypeName(String entityTypeName) {
    return collections.get(entityTypeName);
  }

  public Optional<Collection> getCollectionForCollectionName(String collectionName) {
    return this.collections.values().stream()
      .filter(x-> Objects.equals(x.getCollectionName(), collectionName))
      .findAny();
  }



  public Set<String> getEntityTypes() {
    return collections.keySet();
  }

  public String getVreName() {
    return vreName;
  }

  public Optional<Collection> getImplementerOf(String abstractType) {
    return this.collections.values().stream()
      .filter(x-> Objects.equals(x.getAbstractType(), abstractType))
      .findAny();
  }

  public String getOwnType(String... types) {
    Iterator<String> intersection = Sets.intersection(collections.keySet(), Sets.newHashSet(types)).iterator();
    if (intersection.hasNext()) {
      return intersection.next();
    } else {
      return null;
    }
  }

  public Optional<Collection> getRelationCollection() {
    Iterator<Collection> collectionIt = getCollections()
      .entrySet()
      .stream()
      .filter(entry -> entry.getValue().isRelationCollection())
      .map(Map.Entry::getValue)
      .iterator();

    return collectionIt.hasNext() ? Optional.of(collectionIt.next()) : Optional.empty();
  }

  public void addCollection(Collection collection) {
    collections.put(collection.getEntityTypeName(), collection);
  }

  public Map<String, Collection> getCollections() {
    return collections;
  }

  public Vertex save(Graph graph, Optional<Map<String, String>> keywordTypes) {
    LOG.info("Persisting vre '{}' to database", vreName);

    Vertex vreVertex = findOrCreateVreVertex(graph);

    saveProperties(keywordTypes, vreVertex);

    saveCollections(graph, vreVertex);

    return vreVertex;
  }

  private void saveCollections(Graph graphWrapper, Vertex vreVertex) {
    getCollections().forEach((name, collection) -> {
      LOG.debug("Adding collection {} to VRE {}", name, vreName);
      vreVertex.addEdge(HAS_COLLECTION_RELATION_NAME, collection.save(graphWrapper));
    });
  }

  private void saveProperties(Optional<Map<String, String>> keywordTypes, Vertex vreVertex) {
    vreVertex.property(VRE_NAME_PROPERTY_NAME, vreName);
    if (keywordTypes.isPresent()) {
      try {
        vreVertex.property(KEYWORD_TYPES_PROPERTY_NAME, new ObjectMapper().writeValueAsString(keywordTypes.get()));
      } catch (JsonProcessingException e) {
        LOG.error("Failed to serialize keyword types to JSON {}", keywordTypes.get());
      }
    }
  }

  private Vertex findOrCreateVreVertex(Graph graph) {
    // Look for existing VRE vertex
    Vertex vreVertex;
    GraphTraversal<Vertex, Vertex> existing = graph.traversal().V().hasLabel(DATABASE_LABEL)
                                                   .has(VRE_NAME_PROPERTY_NAME, vreName);
    // Create new if does not exist
    if (existing.hasNext()) {
      vreVertex = existing.next();
      LOG.debug("Replacing existing vertex {}.", vreVertex);
    } else {
      vreVertex = graph.addVertex(DATABASE_LABEL);
      LOG.debug("Creating new vertex");
    }
    return vreVertex;
  }
}
