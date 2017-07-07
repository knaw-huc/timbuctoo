package nl.knaw.huygens.timbuctoo.v5.bdbdatafetchers;

import nl.knaw.huygens.timbuctoo.v5.bdbdatafetchers.datafetchers.CollectionDataFetcher;
import nl.knaw.huygens.timbuctoo.v5.bdbdatafetchers.datafetchers.EnityDataFetcher;
import nl.knaw.huygens.timbuctoo.v5.bdbdatafetchers.datafetchers.RelationDataFetcher;
import nl.knaw.huygens.timbuctoo.v5.bdbdatafetchers.datafetchers.TypedLiteralDataFetcher;
import nl.knaw.huygens.timbuctoo.v5.bdbdatafetchers.datafetchers.UnionDataFetcher;
import nl.knaw.huygens.timbuctoo.v5.bdbdatafetchers.datafetchers.UriDataFetcher;
import nl.knaw.huygens.timbuctoo.v5.dataset.QuadStore;
import nl.knaw.huygens.timbuctoo.v5.dataset.SubjectStore;
import nl.knaw.huygens.timbuctoo.v5.graphql.datafetchers.CollectionFetcher;
import nl.knaw.huygens.timbuctoo.v5.graphql.datafetchers.DataFetcherFactory;
import nl.knaw.huygens.timbuctoo.v5.graphql.datafetchers.EntityFetcher;
import nl.knaw.huygens.timbuctoo.v5.graphql.datafetchers.RelatedDataFetcher;
import nl.knaw.huygens.timbuctoo.v5.graphql.datafetchers.UriFetcher;

public class DataStoreDataFetcherFactory implements DataFetcherFactory {
  private final QuadStore tripleStore;
  private final SubjectStore collectionIndex;

  public DataStoreDataFetcherFactory(QuadStore tripleStore, SubjectStore collectionIndex) {
    this.tripleStore = tripleStore;
    this.collectionIndex = collectionIndex;
  }

  @Override
  public CollectionFetcher collectionFetcher(String typeUri) {
    return new CollectionDataFetcher(typeUri, collectionIndex);
  }

  @Override
  public EntityFetcher entityFetcher() {
    return new EnityDataFetcher();
  }

  @Override
  public RelatedDataFetcher relationFetcher(String predicate) {
    return new RelationDataFetcher(predicate, tripleStore);
  }

  @Override
  public RelatedDataFetcher typedLiteralFetcher(String predicate) {
    return new TypedLiteralDataFetcher(predicate, tripleStore);
  }

  @Override
  public RelatedDataFetcher unionFetcher(String predicate) {
    return new UnionDataFetcher(predicate, tripleStore);
  }

  @Override
  public UriFetcher entityUriDataFetcher() {
    return new UriDataFetcher();
  }
}
