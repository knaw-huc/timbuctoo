package nl.knaw.huygens.timbuctoo.v5.graphql.derivedschema;

import com.google.common.collect.Lists;
import nl.knaw.huygens.timbuctoo.v5.datastores.prefixstore.TypeNameStore;
import nl.knaw.huygens.timbuctoo.v5.datastores.schemastore.dto.Predicate;

import java.util.ArrayList;
import java.util.Set;

public class DerivedCompositeObjectTypeSchemaGenerator implements DerivedObjectTypeSchemaGenerator {

  private final ArrayList<DerivedObjectTypeSchemaGenerator> delegates;

  public DerivedCompositeObjectTypeSchemaGenerator(String typeUri,
                                                   TypeNameStore nameStore, String rootType,
                                                   DerivedSchemaContainer derivedSchemaContainer) {
    delegates = Lists.newArrayList(
      new DerivedQueryObjectTypeSchemaGenerator(typeUri, nameStore, rootType, derivedSchemaContainer)
    // new DerivedInputTypeSchemaGenerator(typeUri, nameStore),
    // new DerivedObjectTypeOperationsSchemaGenerator(typeUri, nameStore, rootType)
    );
  }

  @Override
  public void open() {
    delegates.forEach(DerivedObjectTypeSchemaGenerator::open);
  }

  @Override
  public void close() {
    delegates.forEach(DerivedObjectTypeSchemaGenerator::close);
  }

  @Override
  public void objectField(String description, Predicate predicate, String typeUri) {
    delegates.forEach(delegate -> delegate.objectField(description, predicate, typeUri));
  }

  @Override
  public void unionField(String description, Predicate predicate, Set<String> typeUris) {
    delegates.forEach(delegate -> delegate.unionField(description, predicate, typeUris));
  }

  @Override
  public void valueField(String description, Predicate predicate, String typeUri) {
    delegates.forEach(delegate -> delegate.valueField(description, predicate, typeUri));
  }

  @Override
  public StringBuilder getSchema() {
    StringBuilder schema = new StringBuilder();

    delegates.forEach(delegate -> schema.append(delegate.getSchema()).append("\n"));

    return schema;
  }
}
