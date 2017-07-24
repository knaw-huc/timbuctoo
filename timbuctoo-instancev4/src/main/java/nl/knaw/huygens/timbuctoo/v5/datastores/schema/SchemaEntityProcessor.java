package nl.knaw.huygens.timbuctoo.v5.datastores.schema;

import com.google.common.collect.ListMultimap;
import nl.knaw.huygens.timbuctoo.v5.dataset.EntityProcessor;
import nl.knaw.huygens.timbuctoo.v5.dataset.PredicateData;
import nl.knaw.huygens.timbuctoo.v5.dataset.PredicateHandler;
import nl.knaw.huygens.timbuctoo.v5.datastores.schema.dto.Predicate;
import nl.knaw.huygens.timbuctoo.v5.datastores.schema.dto.Type;
import nl.knaw.huygens.timbuctoo.v5.util.RdfConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static nl.knaw.huygens.timbuctoo.v5.util.RdfConstants.RDF_TYPE;
import static nl.knaw.huygens.timbuctoo.v5.util.RdfConstants.UNKNOWN;

public class SchemaEntityProcessor implements EntityProcessor {
  private static final Logger LOG = LoggerFactory.getLogger(SchemaEntityProcessor.class);
  private static final Function<String, Type> TYPE_MAKER = Type::new;
  private final SchemaUpdater schemaUpdater;
  private Map<String, Type> types = new HashMap<>();

  public SchemaEntityProcessor(SchemaUpdater schemaUpdater) {
    this.schemaUpdater = schemaUpdater;
  }

  @Override
  public void start() {
    LOG.info("Processing entities");
  }

  @Override
  public void processEntity(String cursor, String subjectUri, ListMultimap<String, PredicateData> addedPredicates,
                            Map<String, Boolean> inverseLists) {
    Map<String, Type> curTypes = new HashMap<>();
    List<PredicateData> subjectTypes = addedPredicates.get(RDF_TYPE);
    //create all the types that this subject belongs to
    if (subjectTypes.isEmpty()) {
      curTypes.put(UNKNOWN, types.computeIfAbsent(UNKNOWN, TYPE_MAKER));
    } else {
      for (PredicateData type : subjectTypes) {
        type.handle(new PredicateHandler() {
          @Override
          public void onRelation(String uri, List<String> typesOfRelation) {
            curTypes.put(uri, types.computeIfAbsent(uri, TYPE_MAKER));
          }

          @Override
          public void onValue(String value, String dataType) {
          }

          @Override
          public void onLanguageTaggedString(String value, String language) {
          }
        });
      }
    }
    if (addedPredicates.size() > 100) {
      LOG.info("There's " + addedPredicates.size() + " predicates!");
    }
    for (Type type : curTypes.values()) {
      for (Map.Entry<String, Boolean> inversePredicate : inverseLists.entrySet()) {
        Predicate predicate = type.getOrCreatePredicate(inversePredicate.getKey() + "_inverse");
        predicate.setList(inversePredicate.getValue());
        predicate.incUsage();
      }

      for (String predicateUri : addedPredicates.keys()) {
        final Predicate predicate = type.getOrCreatePredicate(predicateUri);
        List<PredicateData> predicateValues = addedPredicates.get(predicateUri);
        if (predicateValues.size() > 1) {
          predicate.setList(true);
        }
        predicate.incUsage();
        for (PredicateData predicateValue : predicateValues) {
          predicateValue.handle(new PredicateHandler() {
            @Override
            public void onRelation(String uri, List<String> types1) {
              if (types1.isEmpty()) {
                predicate.addReferenceType(UNKNOWN);
              } else {
                for (String type1 : types1) {
                  predicate.addReferenceType(type1);
                }
              }
            }

            @Override
            public void onValue(String value, String dataType) {
              predicate.addValueType(dataType);
            }

            @Override
            public void onLanguageTaggedString(String value, String language) {
              predicate.addValueType(RdfConstants.LANGSTRING);
            }
          });
        }
      }
    }
  }

  @Override
  public void finish() {
    LOG.info("Finished processing entities");
    for (Map.Entry<String, Type> typeEntry : types.entrySet()) {
      Type type = typeEntry.getValue();
      String typeName = typeEntry.getKey();

      for (Predicate predicate : type.getPredicates().values()) {
        if (predicate.getName().endsWith("_inverse") || RdfConstants.RDF_TYPE.equals(predicate.getName())) {
          continue;
        }
        for (String referenceType : predicate.getReferenceTypes()) {
          try {
            types.get(referenceType)
              .getPredicates().get(predicate.getName() + "_inverse")
              .addReferenceType(typeName);
          } catch (Exception e) {
            String cause = "Referenced type not found";
            try {
              if (types.containsKey(referenceType)) {
                cause = "type has no predicates";
                if (types.get(referenceType).getPredicates() != null) {
                  cause = "type does not have the predicate " + predicate.getName() + "_inverse";
                  if (types.get(referenceType).getPredicates().containsKey(predicate.getName() + "_inverse")) {
                    cause = "Something failed during addreferencetype(" + typeName + ")";
                  }
                }
              }
              LOG.error("Error during inverse generation (ignored): " + cause , e);
            } catch (Exception e2) {
              LOG.error("Error during inverse generation " + cause, e);
              LOG.error("Error during recovery generation ", e2);
            }
          }
        }
      }
    }
    try {
      schemaUpdater.replaceSchema(types);
    } catch (SchemaUpdateException e) {
      e.printStackTrace();
    }
  }
}
