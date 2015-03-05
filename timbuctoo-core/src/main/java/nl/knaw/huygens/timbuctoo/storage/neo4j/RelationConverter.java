package nl.knaw.huygens.timbuctoo.storage.neo4j;

import static nl.knaw.huygens.timbuctoo.model.Entity.ID_PROPERTY_NAME;

import java.util.List;

import nl.knaw.huygens.timbuctoo.model.Entity;
import nl.knaw.huygens.timbuctoo.model.Relation;

import org.neo4j.graphdb.Label;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Relationship;

import com.google.common.collect.Lists;

public class RelationConverter<T extends Relation, U extends Relationship> implements EntityConverter<T, U> {

  private List<String> fieldsToIgnore;
  private List<FieldConverter> fieldConverters;

  public RelationConverter(List<String> fieldsToIgnore) {
    this.fieldsToIgnore = fieldsToIgnore;
    fieldConverters = Lists.newArrayList();
  }

  @Override
  public void addValuesToPropertyContainer(U propertyContainer, T entity) throws ConversionException {
    for (FieldConverter fieldConverter : fieldConverters) {
      if (!fieldsToIgnore.contains(fieldConverter.getName())) {
        fieldConverter.setPropertyContainerProperty(propertyContainer, entity);
      }
    }
  }

  @Override
  public void addValuesToEntity(T entity, U propertyContainer) throws ConversionException {
    for (FieldConverter fieldConverter : fieldConverters) {
      if (!fieldsToIgnore.contains(fieldConverter.getName())) {
        fieldConverter.addValueToEntity(entity, propertyContainer);
      }
    }

    Node startNode = propertyContainer.getStartNode();
    if (startNode.hasProperty(ID_PROPERTY_NAME)) {
      entity.setSourceId((String) startNode.getProperty(ID_PROPERTY_NAME));
    }

    entity.setSourceType(getPrimitiveType(startNode));

    Node endNode = propertyContainer.getEndNode();
    if (endNode.hasProperty(ID_PROPERTY_NAME)) {
      entity.setTargetId((String) endNode.getProperty(ID_PROPERTY_NAME));
    }
    entity.setTargetType(getPrimitiveType(endNode));
  }

  private String getPrimitiveType(Node node) {
    String name = null;

    // TODO: find a neater way to determine the name of the primitive type see TIM-63
    for (Label label : node.getLabels()) {
      if (name == null || label.name().length() < name.length()) {
        name = label.name();
      }
    }
    return name;
  }

  @Override
  public void addFieldConverter(FieldConverter fieldConverter) {
    this.fieldConverters.add(fieldConverter);
  }

  @Override
  public void updatePropertyContainer(U propertyContainer, Entity entity) throws ConversionException {
    throw new UnsupportedOperationException("Yet to be implemented");
  }

  @Override
  public void updateModifiedAndRev(U propertyContainer, Entity entity) throws ConversionException {
    throw new UnsupportedOperationException("Yet to be implemented");
  }

}
