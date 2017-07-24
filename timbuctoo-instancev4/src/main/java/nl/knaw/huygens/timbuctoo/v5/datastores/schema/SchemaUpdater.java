package nl.knaw.huygens.timbuctoo.v5.datastores.schema;

import nl.knaw.huygens.timbuctoo.v5.datastores.schema.dto.Type;

import java.util.Map;

public interface SchemaUpdater {
  void replaceSchema(Map<String, Type> newSchema) throws SchemaUpdateException;
}
