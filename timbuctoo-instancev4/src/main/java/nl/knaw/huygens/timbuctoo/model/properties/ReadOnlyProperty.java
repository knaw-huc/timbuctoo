package nl.knaw.huygens.timbuctoo.model.properties;

import com.fasterxml.jackson.databind.JsonNode;
import javaslang.control.Try;
import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversal;

import java.util.function.Supplier;

public abstract class ReadOnlyProperty {
  private final Supplier<GraphTraversal<?, Try<JsonNode>>> getter;

  public ReadOnlyProperty(Supplier<GraphTraversal<?, Try<JsonNode>>> getter) {
    this.getter = getter;
  }

  public GraphTraversal<?, Try<JsonNode>> get() {
    return getter.get();
  }

}
