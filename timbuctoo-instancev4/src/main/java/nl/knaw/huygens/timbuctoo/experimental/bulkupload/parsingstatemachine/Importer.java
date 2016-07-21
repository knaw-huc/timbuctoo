package nl.knaw.huygens.timbuctoo.experimental.bulkupload.parsingstatemachine;

import nl.knaw.huygens.timbuctoo.experimental.bulkupload.savers.Saver;
import org.apache.tinkerpop.gremlin.structure.Vertex;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

public class Importer {
  private Set<Integer> idsToSkip = new HashSet<>();
  private ImportPropertyDescriptions properties;
  private final Saver saver;
  private Vertex currentCollection;
  private String[] currentProperties;

  private ImportState currentState = ImportState.NOTHING;

  public Importer(Saver saver) {
    this.saver = saver;
  }

  public Result startCollection(String collectionName) {
    if (currentState != ImportState.NOTHING) {
      return Result.failure("I was not expecting a collection declaration here");
    }
    if (collectionName.equals("")) {
      return Result.failure("This collection has no name!");
    }
    currentCollection = saver.addCollection(collectionName);
    currentState = ImportState.GETTING_DECLARATION;
    properties = new ImportPropertyDescriptions();
    return Result.success();
  }

  public Result registerPropertyName(int id, String name) {
    if (idsToSkip.contains(id) || currentState == ImportState.SKIPPING) {
      return Result.ignored();
    }
    if (currentState != ImportState.GETTING_DECLARATION) {
      return Result.failure("I was not expecting a property declaration here");
    }
    properties.getOrCreate(id).setPropertyName(name);
    return Result.success();
  }

  public void startEntity() {
    if (currentState == ImportState.GETTING_DECLARATION) {
      currentState = ImportState.GETTING_VALUES;
    }

    currentProperties = new String[properties.getPropertyCount()];
  }

  public Result setValue(int id, String value) {
    if (currentState != ImportState.GETTING_VALUES) {
      return Result.failure("I was not expecting a value property here");
    }
    if (idsToSkip.contains(id) || currentState == ImportState.SKIPPING) {
      return Result.ignored();
    }
    Optional<ImportPropertyDescription> propOpt = properties.get(id);
    if (propOpt.isPresent()) {
      ImportPropertyDescription prop = propOpt.get();
      currentProperties[prop.getOrder()] = value;
      return Result.ignored();//actual validation will happen during finishEntity
    } else {
      return Result.failure("No property declared for id " + id);
    }
  }

  public HashMap<Integer, Result> finishEntity() {
    HashMap<String, Object> propertyValues = new HashMap<>();
    HashMap<Integer, Result> results = new HashMap<>();
    for (int i = 0, currentPropertiesLength = currentProperties.length; i < currentPropertiesLength; i++) {
      String value = currentProperties[i];
      if (value != null) {
        ImportPropertyDescription desc = properties.getByOrder(i);
        propertyValues.put("rw" + desc.getPropertyName(), value);
        results.put(desc.getId(), Result.success());
      }
    }

    saver.addEntity(currentCollection, propertyValues);

    return results;
  }

  public void finishCollection() {
    currentState = ImportState.NOTHING;
    currentCollection = null;
  }

  private enum ImportState {
    NOTHING,
    GETTING_DECLARATION,
    GETTING_VALUES,
    SKIPPING
  }
}