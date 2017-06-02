package nl.knaw.huygens.timbuctoo.v5.graphql.collectionindex;

import graphql.Scalars;
import graphql.schema.GraphQLFieldDefinition;
import graphql.schema.GraphQLObjectType;
import nl.knaw.huygens.timbuctoo.v5.graphql.datafetchers.CollectionFetcherWrapper;
import nl.knaw.huygens.timbuctoo.v5.graphql.datafetchers.DataFetcherFactory;
import nl.knaw.huygens.timbuctoo.v5.graphql.datafetchers.LookupFetcher;
import nl.knaw.huygens.timbuctoo.v5.graphql.datafetchers.PaginationArgumentsHelper;

import java.util.Map;

import static graphql.schema.GraphQLArgument.newArgument;
import static graphql.schema.GraphQLFieldDefinition.newFieldDefinition;
import static graphql.schema.GraphQLNonNull.nonNull;
import static graphql.schema.GraphQLObjectType.newObject;

public class CollectionIndexSchemaFactory {

  public GraphQLObjectType createQuerySchema(Map<String, GraphQLObjectType> typesMap,
                                             DataFetcherFactory fetcherFactory,
                                             PaginationArgumentsHelper paginationArgumentsHelper) {

    GraphQLObjectType.Builder result = newObject()
      .name("Query");

    for (Map.Entry<String, GraphQLObjectType> typeMapping : typesMap.entrySet()) {
      String typeUri = typeMapping.getKey();
      GraphQLObjectType objectType = typeMapping.getValue();
      String typeName = objectType.getName();

      GraphQLFieldDefinition.Builder collectionField = newFieldDefinition()
        .name(typeName + "List")
        .dataFetcher(new CollectionFetcherWrapper(fetcherFactory.collectionFetcher(typeUri)));
      paginationArgumentsHelper.makePaginatedList(collectionField, objectType);
      result.field(collectionField);

      String uriArgument = "uri";
      GraphQLFieldDefinition.Builder lookupField = newFieldDefinition()
        .name(typeName)
        .type(objectType)
        .dataFetcher(new LookupFetcher(fetcherFactory.entityFetcher(), uriArgument))
        .argument(
          newArgument()
            .name(uriArgument)
            .type(nonNull(Scalars.GraphQLID))
            .description("The uri of the item that you wish to retrieve")
        );
      result.field(lookupField);
    }

    return result.build();
  }

}
