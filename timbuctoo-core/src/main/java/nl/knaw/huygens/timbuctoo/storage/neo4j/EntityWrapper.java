package nl.knaw.huygens.timbuctoo.storage.neo4j;

import java.util.List;

import nl.knaw.huygens.timbuctoo.model.SystemEntity;

import org.neo4j.graphdb.DynamicLabel;
import org.neo4j.graphdb.Node;

import com.google.common.collect.Lists;

public class EntityWrapper {

  private List<FieldWrapper> fieldWrappers;
  private SystemEntity entity;
  private NameCreator nameCreator;

  public EntityWrapper() {
    fieldWrappers = Lists.newArrayList();
  }

  public void addValuesToNode(Node node) throws IllegalArgumentException, IllegalAccessException {
    addName(node);
    for (FieldWrapper fieldWrapper : fieldWrappers) {
      fieldWrapper.addValueToNode(node);
    }
  }

  private void addName(Node node) {
    node.addLabel(DynamicLabel.label(nameCreator.internalTypeName(entity.getClass())));
  }

  public String addAdministrativeValues(Node node) {
    // TODO Auto-generated method stub
    return null;
  }

  public void addFieldWrapper(FieldWrapper fieldWrapper) {
    fieldWrappers.add(fieldWrapper);
  }

  public void setEntity(SystemEntity entity) {
    this.entity = entity;
  }

  public void setNameCreator(NameCreator nameCreator) {
    this.nameCreator = nameCreator;
  }

}