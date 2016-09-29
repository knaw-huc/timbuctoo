package nl.knaw.huygens.timbuctoo.database;

import com.google.common.collect.Lists;
import nl.knaw.huygens.timbuctoo.crud.NotFoundException;
import nl.knaw.huygens.timbuctoo.database.dto.ReadEntity;
import nl.knaw.huygens.timbuctoo.database.dto.dataset.Collection;
import org.junit.Before;
import org.junit.Test;

import java.util.UUID;
import java.util.stream.Stream;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.sameInstance;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class TimbuctooDbAccessGetTest {

  private static final UUID ID = UUID.randomUUID();
  private static final Integer rev = 1;
  private CustomEntityProperties entityProps;
  private CustomRelationProperties relationProps;
  private DataAccess dataAccess;
  private TimbuctooDbAccess instance;
  private Collection collection;
  private boolean withRelations;

  @Before
  public void setUp() throws Exception {
    entityProps = mock(CustomEntityProperties.class);
    relationProps = mock(CustomRelationProperties.class);
    dataAccess = mock(DataAccess.class);
    instance = new TimbuctooDbAccess(null, dataAccess, null, null);
    collection = mock(Collection.class);
  }

  @Test(expected = NotFoundException.class)
  public void getEntityThrowsANotFoundExceptionWhenTheEntityCannotBeFound() throws Exception {
    when(dataAccess.getEntity(collection, ID, rev, entityProps, relationProps)).thenReturn(GetMessage.notFound());

    instance.getEntity(collection, ID, rev, entityProps, relationProps);
  }

  @Test
  public void getEntityReturnsTheReadEntity() throws Exception {
    ReadEntity readEntity = mock(ReadEntity.class);
    when(dataAccess.getEntity(collection, ID, rev, entityProps, relationProps))
      .thenReturn(GetMessage.success(readEntity));

    ReadEntity actualEntity = instance.getEntity(collection, ID, rev, entityProps, relationProps);

    assertThat(actualEntity, is(sameInstance(readEntity)));
  }

  @Test
  public void getCollectionLetsReturnsACollection() {
    Stream<ReadEntity> entities = Lists.<ReadEntity>newArrayList().stream();
    int start = 0;
    int rows = 10;
    when(dataAccess.getCollection(collection, start, rows, withRelations, entityProps, relationProps))
      .thenReturn(entities);

    Stream<ReadEntity> result =
      instance.getCollection(collection, start, rows, withRelations, entityProps, relationProps);

    assertThat(result, is(sameInstance(entities)));
  }


}
