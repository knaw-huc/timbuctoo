package nl.knaw.huygens.timbuctoo.database;

import nl.knaw.huygens.timbuctoo.crud.HandleAdder;
import nl.knaw.huygens.timbuctoo.crud.NotFoundException;
import nl.knaw.huygens.timbuctoo.database.dto.CreateRelation;
import nl.knaw.huygens.timbuctoo.database.dto.UpdateRelation;
import nl.knaw.huygens.timbuctoo.database.dto.dataset.Collection;
import nl.knaw.huygens.timbuctoo.security.AuthorizationException;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import static nl.knaw.huygens.timbuctoo.database.AuthorizerBuilder.allowedToWrite;
import static nl.knaw.huygens.timbuctoo.database.AuthorizerBuilder.notAllowedToWrite;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.hasProperty;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.Assert.assertThat;
import static org.mockito.Matchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

public class TimbuctooDbAccessRelationTest {

  private static final String USER_ID = "userId";
  private DataAccess dataAccess;
  private Clock clock;
  private HandleAdder handleAdder;
  private CreateRelation createRelation;
  private Collection collection;
  private Instant instant;

  @Before
  public void setUp() throws Exception {
    dataAccess = mock(DataAccess.class);
    clock = mock(Clock.class);
    instant = Instant.now();
    when(clock.instant()).thenReturn(instant);
    handleAdder = mock(HandleAdder.class);
    createRelation = new CreateRelation(null, null, null);
    collection = mock(Collection.class);
  }

  @Test
  public void createRelationCreatesANewRelation() throws Exception {
    when(dataAccess.createRelation(collection, createRelation)).thenReturn(CreateMessage.success(UUID.randomUUID()));
    TimbuctooDbAccess instance = new TimbuctooDbAccess(allowedToWrite(), dataAccess, clock, handleAdder);

    instance.createRelation(collection, createRelation, USER_ID);

    verify(dataAccess).createRelation(argThat(is(collection)), argThat(allOf(
      hasProperty("created", allOf(
        hasProperty("userId", is(USER_ID)),
        hasProperty("timeStamp", is(instant.toEpochMilli()))
      ))
    )));

  }

  @Test
  public void createRelationReturnsTheIdOfTheNewLyCreatedRelation() throws Exception {
    when(dataAccess.createRelation(collection, createRelation)).thenReturn(CreateMessage.success(UUID.randomUUID()));
    TimbuctooDbAccess instance = new TimbuctooDbAccess(allowedToWrite(), dataAccess, clock, handleAdder);

    UUID id = instance.createRelation(collection, createRelation, USER_ID);

    assertThat(id, is(notNullValue(UUID.class)));
  }

  @Test(expected = AuthorizationException.class)
  public void createRelationThrowsAnUnauthorizedExceptionWhenTheUserIsNotAllowedToWrite() throws Exception {
    TimbuctooDbAccess instance = new TimbuctooDbAccess(notAllowedToWrite(), dataAccess, clock, handleAdder);

    try {
      instance.createRelation(collection, createRelation, USER_ID);
    } finally {
      verifyZeroInteractions(dataAccess);
    }
  }

  @Test(expected = IOException.class)
  public void createRelationsThrowsAnIoExceptionWithTheMessageOfTheReturnValueIfTheRelationsCouldNotBeCreated()
    throws Exception {
    when(dataAccess.createRelation(collection, createRelation)).thenReturn(CreateMessage.failure("error message"));
    TimbuctooDbAccess instance = new TimbuctooDbAccess(allowedToWrite(), dataAccess, clock, handleAdder);

    instance.createRelation(collection, createRelation, USER_ID);
  }

  @Test(expected = AuthorizationException.class)
  public void replaceRelationThrowsAnAuthorizationExceptionWhenTheUsersIsNotAllowedToWrite() throws Exception {
    TimbuctooDbAccess instance = new TimbuctooDbAccess(notAllowedToWrite(), dataAccess, clock, handleAdder);

    try {
      instance.replaceRelation(collection, new UpdateRelation(UUID.randomUUID(), 1, false), USER_ID);
    } finally {
      verifyZeroInteractions(dataAccess);
    }
  }

  @Test
  public void replaceRelationUpdatesARelation() throws Exception {
    UUID id = UUID.randomUUID();
    UpdateRelation updateRelation = new UpdateRelation(id, 1, false);
    when(dataAccess.updateRelation(collection, updateRelation)).thenReturn(UpdateReturnMessage.success(1));
    TimbuctooDbAccess instance = new TimbuctooDbAccess(allowedToWrite(), dataAccess, clock, handleAdder);

    instance.replaceRelation(collection, updateRelation, USER_ID);

    verify(dataAccess).updateRelation(argThat(is(collection)), argThat(allOf(
      hasProperty("id", is(id)),
      hasProperty("modified", allOf(
        hasProperty("userId", is(USER_ID)),
        hasProperty("timeStamp", is(instant.toEpochMilli()))
      ))
    )));
  }

  @Test(expected = NotFoundException.class)
  public void replaceRelationThrowsANotFoundExceptionWhenTheRelationCannotBeFound() throws Exception {
    UpdateRelation updateRelation = new UpdateRelation(null, 1, false);
    when(dataAccess.updateRelation(collection, updateRelation)).thenReturn(UpdateReturnMessage.notFound());
    TimbuctooDbAccess instance = new TimbuctooDbAccess(allowedToWrite(), dataAccess, clock, handleAdder);

    instance.replaceRelation(collection, updateRelation, USER_ID);
  }

}
