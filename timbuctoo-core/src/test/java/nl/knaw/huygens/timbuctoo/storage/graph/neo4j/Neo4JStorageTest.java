package nl.knaw.huygens.timbuctoo.storage.graph.neo4j;

import static nl.knaw.huygens.timbuctoo.storage.graph.DomainEntityMatcher.likeDomainEntity;
import static nl.knaw.huygens.timbuctoo.storage.graph.SubADomainEntityBuilder.aDomainEntity;
import static nl.knaw.huygens.timbuctoo.storage.graph.SubARelationBuilder.aRelation;
import static nl.knaw.huygens.timbuctoo.storage.graph.TestSystemEntityWrapperBuilder.aSystemEntity;
import static nl.knaw.huygens.timbuctoo.storage.graph.neo4j.Neo4JStorage.REQUEST_TIMEOUT;
import static nl.knaw.huygens.timbuctoo.storage.graph.neo4j.NodeMockBuilder.aNode;
import static nl.knaw.huygens.timbuctoo.storage.graph.neo4j.NodeSearchResultBuilder.aNodeSearchResult;
import static nl.knaw.huygens.timbuctoo.storage.graph.neo4j.NodeSearchResultBuilder.anEmptyNodeSearchResult;
import static nl.knaw.huygens.timbuctoo.storage.graph.neo4j.RelationshipMockBuilder.aRelationship;
import static nl.knaw.huygens.timbuctoo.storage.graph.neo4j.RelationshipTypeMatcher.likeRelationshipType;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.sameInstance;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.argThat;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;

import nl.knaw.huygens.timbuctoo.config.TypeNames;
import nl.knaw.huygens.timbuctoo.config.TypeRegistry;
import nl.knaw.huygens.timbuctoo.model.DomainEntity;
import nl.knaw.huygens.timbuctoo.model.Entity;
import nl.knaw.huygens.timbuctoo.model.Relation;
import nl.knaw.huygens.timbuctoo.model.RelationType;
import nl.knaw.huygens.timbuctoo.model.util.Change;
import nl.knaw.huygens.timbuctoo.storage.NoSuchEntityException;
import nl.knaw.huygens.timbuctoo.storage.StorageException;
import nl.knaw.huygens.timbuctoo.storage.StorageIterator;
import nl.knaw.huygens.timbuctoo.storage.UpdateException;
import nl.knaw.huygens.timbuctoo.storage.graph.ConversionException;
import nl.knaw.huygens.timbuctoo.storage.graph.IdGenerator;
import nl.knaw.huygens.timbuctoo.storage.graph.neo4j.conversion.PropertyContainerConverterFactory;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.mockito.InOrder;
import org.neo4j.graphdb.DynamicLabel;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Label;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Relationship;
import org.neo4j.graphdb.ResourceIterator;
import org.neo4j.graphdb.Transaction;

import test.model.BaseDomainEntity;
import test.model.TestSystemEntityWrapper;
import test.model.projecta.SubADomainEntity;
import test.model.projecta.SubARelation;

import com.google.common.collect.Lists;

public class Neo4JStorageTest {

  private static final String RELATION_FIELD_NAME = SubARelation.SOURCE_ID;
  private static final String PROPERTY_NAME = "propertyName";
  private static final String PROPERTY_VALUE = "Test";
  private static final String DOMAIN_ENTITY_FIELD = SubADomainEntity.VALUEA2_NAME;
  private static final Class<BaseDomainEntity> PRIMITIVE_DOMAIN_ENTITY_TYPE = BaseDomainEntity.class;
  private static final String PRIMITIVE_DOMAIN_ENTITY_NAME = TypeNames.getInternalName(PRIMITIVE_DOMAIN_ENTITY_TYPE);
  private static final Label PRIMITIVE_DOMAIN_ENTITY_LABEL = DynamicLabel.label(PRIMITIVE_DOMAIN_ENTITY_NAME);

  private static final Class<TestSystemEntityWrapper> SYSTEM_ENTITY_TYPE = TestSystemEntityWrapper.class;
  private static final Label SYSTEM_ENTITY_LABEL = DynamicLabel.label(TypeNames.getInternalName(SYSTEM_ENTITY_TYPE));
  private static final Class<SubADomainEntity> DOMAIN_ENTITY_TYPE = SubADomainEntity.class;
  private static final Label DOMAIN_ENTITY_LABEL = DynamicLabel.label(TypeNames.getInternalName(DOMAIN_ENTITY_TYPE));
  private static final String ID = "id";
  private static final int FIRST_REVISION = 1;
  private static final int SECOND_REVISION = 2;
  private static final int THIRD_REVISION = 3;
  private static final String PID = "pid";
  private static final Change CHANGE = Change.newInternalInstance();

  private static final Class<Relationship> RELATIONSHIP_TYPE = Relationship.class;
  private static final String RELATION_TYPE_ID = "typeId";
  private static final String RELATION_TARGET_ID = "targetId";
  private static final String RELATION_SOURCE_ID = "sourceId";
  private static final Class<SubARelation> RELATION_TYPE = SubARelation.class;
  private static final Class<RelationType> RELATIONTYPE_TYPE = RelationType.class;
  private static final String RELATION_TYPE_NAME = TypeNames.getInternalName(RELATIONTYPE_TYPE);

  private Neo4JStorage instance;
  private PropertyContainerConverterFactory propertyContainerConverterFactoryMock;
  private Transaction transactionMock = mock(Transaction.class);
  private GraphDatabaseService dbMock;
  private NodeDuplicator nodeDuplicatorMock;
  private RelationshipDuplicator relationshipDuplicatorMock;
  private IdGenerator idGeneratorMock;
  private Neo4JLowLevelAPI neo4JLowLevelAPIMock;
  private Neo4JStorageIteratorFactory neo4jStorageIteratorFactoryMock;

  @Before
  public void setup() throws Exception {
    neo4jStorageIteratorFactoryMock = mock(Neo4JStorageIteratorFactory.class);
    neo4JLowLevelAPIMock = mock(Neo4JLowLevelAPI.class);
    nodeDuplicatorMock = mock(NodeDuplicator.class);
    relationshipDuplicatorMock = mock(RelationshipDuplicator.class);
    idGeneratorMock = mock(IdGenerator.class);
    setupEntityConverterFactory();
    setupDBTransaction();
    TypeRegistry typeRegistry = TypeRegistry.getInstance().init("timbuctoo.model test.model test.model.projecta");
    instance = new Neo4JStorage(dbMock, propertyContainerConverterFactoryMock, nodeDuplicatorMock, relationshipDuplicatorMock, idGeneratorMock, typeRegistry, neo4JLowLevelAPIMock,
        neo4jStorageIteratorFactoryMock);
  }

  private void setupDBTransaction() {
    transactionMock = mock(Transaction.class);
    dbMock = mock(GraphDatabaseService.class);
    when(dbMock.beginTx()).thenReturn(transactionMock);
  }

  private void idGeneratorMockCreatesIDFor(Class<? extends Entity> type, String id) {
    when(idGeneratorMock.nextIdFor(type)).thenReturn(id);
  }

  @Test
  public void addDomainEntitySavesTheProjectVersionAndThePrimitive() throws Exception {
    // setup
    Node nodeMock = aNode().createdBy(dbMock);
    idGeneratorMockCreatesIDFor(DOMAIN_ENTITY_TYPE, ID);

    NodeConverter<? super SubADomainEntity> compositeConverter = propertyContainerConverterFactoryHasCompositeConverterFor(DOMAIN_ENTITY_TYPE);
    SubADomainEntity domainEntity = aDomainEntity().build();

    // action
    instance.addDomainEntity(DOMAIN_ENTITY_TYPE, domainEntity, CHANGE);

    // verify
    verify(dbMock).beginTx();
    verify(dbMock).createNode();
    verify(compositeConverter).addValuesToPropertyContainer(nodeMock, domainEntity);
    verify(neo4JLowLevelAPIMock).index(nodeMock);
    verify(transactionMock).success();
  }

  private <T extends DomainEntity> NodeConverter<? super T> propertyContainerConverterFactoryHasCompositeConverterFor(Class<T> type) {
    @SuppressWarnings("unchecked")
    NodeConverter<? super T> converter = mock(NodeConverter.class);
    doReturn(converter).when(propertyContainerConverterFactoryMock).createCompositeForType(type);
    return converter;
  }

  @Test(expected = StorageException.class)
  public void addDomainEntityRollsBackTheTransactionAndThrowsAStorageExceptionWhenTheDomainEntityConverterThrowsAConversionException() throws Exception {
    // setup
    Node nodeMock = aNode().createdBy(dbMock);

    idGeneratorMockCreatesIDFor(DOMAIN_ENTITY_TYPE, ID);

    SubADomainEntity domainEntity = aDomainEntity().build();
    NodeConverter<? super SubADomainEntity> compositeConverter = propertyContainerConverterFactoryHasCompositeConverterFor(DOMAIN_ENTITY_TYPE);
    doThrow(ConversionException.class).when(compositeConverter).addValuesToPropertyContainer(nodeMock, domainEntity);

    try {
      // action
      instance.addDomainEntity(DOMAIN_ENTITY_TYPE, domainEntity, CHANGE);
    } finally {
      // verify
      verify(dbMock).beginTx();
      verify(dbMock).createNode();
      verify(compositeConverter).addValuesToPropertyContainer(nodeMock, domainEntity);
      verify(transactionMock).failure();
      verifyNoMoreInteractions(compositeConverter);
    }
  }

  @Test
  public void addSystemEntitySavesTheSystemAsNode() throws Exception {
    Node nodeMock = aNode().createdBy(dbMock);
    idGeneratorMockCreatesIDFor(SYSTEM_ENTITY_TYPE, ID);

    NodeConverter<TestSystemEntityWrapper> systemEntityConverterMock = propertyContainerConverterFactoryHasANodeConverterTypeFor(SYSTEM_ENTITY_TYPE);
    // action
    TestSystemEntityWrapper systemEntity = aSystemEntity().build();
    instance.addSystemEntity(SYSTEM_ENTITY_TYPE, systemEntity);

    // verify
    InOrder inOrder = inOrder(dbMock, transactionMock, systemEntityConverterMock, neo4JLowLevelAPIMock);
    inOrder.verify(dbMock).beginTx();
    inOrder.verify(systemEntityConverterMock).addValuesToPropertyContainer(//
        nodeMock, // 
        systemEntity);
    inOrder.verify(neo4JLowLevelAPIMock).index(nodeMock);
    inOrder.verify(transactionMock).success();
    verifyNoMoreInteractions(systemEntityConverterMock);
  }

  @Test(expected = StorageException.class)
  public void addSystemEntityRollsBackTheTransactionAndThrowsStorageExceptionObjectrapperThrowsAConversionException() throws Exception {
    Node nodeMock = aNode().createdBy(dbMock);

    NodeConverter<TestSystemEntityWrapper> systemEntityConverterMock = propertyContainerConverterFactoryHasANodeConverterTypeFor(SYSTEM_ENTITY_TYPE);

    TestSystemEntityWrapper systemEntity = aSystemEntity().build();
    doThrow(ConversionException.class).when(systemEntityConverterMock).addValuesToPropertyContainer(nodeMock, systemEntity);

    try {
      // action
      instance.addSystemEntity(SYSTEM_ENTITY_TYPE, systemEntity);
    } finally {
      // verify
      verify(dbMock).beginTx();
      verify(systemEntityConverterMock).addValuesToPropertyContainer(nodeMock, systemEntity);
      verifyNoMoreInteractions(systemEntityConverterMock);
      verify(transactionMock).failure();
    }
  }

  @Test
  public void getEntityReturnsTheItemWhenFound() throws Exception {
    Node nodeMock = aNode().build();
    latestNodeFoundFor(SYSTEM_ENTITY_TYPE, ID, nodeMock);

    TestSystemEntityWrapper systemEntity = aSystemEntity().build();
    NodeConverter<TestSystemEntityWrapper> systemEntityConverterMock = propertyContainerConverterFactoryHasANodeConverterTypeFor(SYSTEM_ENTITY_TYPE);
    when(systemEntityConverterMock.convertToEntity(nodeMock)).thenReturn(systemEntity);

    // action
    TestSystemEntityWrapper actualEntity = instance.getEntity(SYSTEM_ENTITY_TYPE, ID);

    // verify
    assertThat(actualEntity, is(equalTo(systemEntity)));

    InOrder inOrder = inOrder(dbMock, systemEntityConverterMock, transactionMock);
    inOrder.verify(systemEntityConverterMock).convertToEntity(nodeMock);
    inOrder.verify(transactionMock).success();
  }

  @Test
  public void getEntityReturnsNullIfNoItemIsFound() throws Exception {
    // setup
    noLatestNodeFoundFor(SYSTEM_ENTITY_TYPE, ID);

    // action
    TestSystemEntityWrapper actualEntity = instance.getEntity(SYSTEM_ENTITY_TYPE, ID);

    // verify
    assertThat(actualEntity, is(nullValue()));

    verify(transactionMock).success();
    verifyZeroInteractions(propertyContainerConverterFactoryMock);
  }

  @Test(expected = ConversionException.class)
  public void getEntityThrowsStorageExceptionWhenEntityWrapperThrowsAConversionException() throws Exception {
    // setup
    Node nodeMock = aNode().build();
    latestNodeFoundFor(SYSTEM_ENTITY_TYPE, ID, nodeMock);

    NodeConverter<TestSystemEntityWrapper> systemEntityConverterMock = propertyContainerConverterFactoryHasANodeConverterTypeFor(SYSTEM_ENTITY_TYPE);
    when(systemEntityConverterMock.convertToEntity(nodeMock)).thenThrow(new ConversionException());

    try {
      // action
      instance.getEntity(SYSTEM_ENTITY_TYPE, ID);
    } finally {
      // verify
      verify(systemEntityConverterMock).convertToEntity(nodeMock);
      verify(transactionMock).failure();
    }
  }

  @Test(expected = StorageException.class)
  public void getEntityThrowsStorageExceptionWhenNodeConverterThrowsAnInstantiationException() throws Exception {
    // setup
    Node nodeMock = aNode().build();
    latestNodeFoundFor(SYSTEM_ENTITY_TYPE, ID, nodeMock);

    NodeConverter<TestSystemEntityWrapper> systemEntityConverterMock = propertyContainerConverterFactoryHasANodeConverterTypeFor(SYSTEM_ENTITY_TYPE);
    doThrow(InstantiationException.class).when(systemEntityConverterMock).convertToEntity(nodeMock);

    try {
      // action
      instance.getEntity(SYSTEM_ENTITY_TYPE, ID);
    } finally {
      // verify
      verify(systemEntityConverterMock).convertToEntity(nodeMock);
      verify(transactionMock).failure();
    }
  }

  private void noLatestNodeFoundFor(Class<? extends Entity> type, String id) {
    when(neo4JLowLevelAPIMock.getLatestNodeById(type, id)).thenReturn(null);
  }

  private void latestNodeFoundFor(Class<? extends Entity> type, String id, Node foundNode) {
    when(neo4JLowLevelAPIMock.getLatestNodeById(type, id)).thenReturn(foundNode);
  }

  @Test
  public void getDefaultVariationReturnsTheRequestedTypeWithTheValuesOfThePrimitiveVariant() throws Exception {
    // setup
    Node node = aNode().build();
    latestNodeFoundFor(PRIMITIVE_DOMAIN_ENTITY_TYPE, ID, node);

    NodeConverter<? super SubADomainEntity> converter = propertyContainerConverterFactoryHasConverterForPrimitiveOf(DOMAIN_ENTITY_TYPE);

    SubADomainEntity entity = aDomainEntity().build();
    when(converter.convertToSubType(DOMAIN_ENTITY_TYPE, node)).thenReturn(entity);

    // action
    SubADomainEntity foundEntity = instance.getDefaultVariation(DOMAIN_ENTITY_TYPE, ID);

    // verify
    assertThat(foundEntity, is(sameInstance(entity)));
    verifyTransactionSucceeded();
  }

  @Test
  public void getDefaultVariationReturnsNullIfThePrimitiveCannotBeFound() throws Exception {
    // setup
    noLatestNodeFoundFor(PRIMITIVE_DOMAIN_ENTITY_TYPE, ID);

    // action
    SubADomainEntity defaultVariation = instance.getDefaultVariation(DOMAIN_ENTITY_TYPE, ID);

    // verify
    assertThat(defaultVariation, is(nullValue()));
    verifyTransactionSucceeded();
  }

  @Test(expected = ConversionException.class)
  public void getDefaultVariationThrowsAConversionExceptionWhenTheNodeCannotBeConverted() throws Exception {
    // setup
    Node node = aNode().build();
    latestNodeFoundFor(PRIMITIVE_DOMAIN_ENTITY_TYPE, ID, node);

    NodeConverter<? super SubADomainEntity> converter = propertyContainerConverterFactoryHasConverterForPrimitiveOf(DOMAIN_ENTITY_TYPE);

    when(converter.convertToSubType(DOMAIN_ENTITY_TYPE, node)).thenThrow(new ConversionException());

    try {
      // action
      instance.getDefaultVariation(DOMAIN_ENTITY_TYPE, ID);
    } finally {
      verifyTransactionFailed();
    }

  }

  private <T extends DomainEntity> NodeConverter<? super T> propertyContainerConverterFactoryHasConverterForPrimitiveOf(Class<T> type) {
    @SuppressWarnings("unchecked")
    NodeConverter<? super T> converter = mock(NodeConverter.class);

    doReturn(converter).when(propertyContainerConverterFactoryMock).createForPrimitive(type);

    return converter;
  }

  @Test
  public void getSystemEntitiesRetrieveAllTheNodesOfACertainTypeAndWrapsThemInAStorageIterator() throws Exception {
    // setup
    Node node1 = aNode().build();
    Node node2 = aNode().build();
    ResourceIterator<Node> searchResult = aNodeSearchResult()//
        .withPropertyContainer(node1)//
        .andPropertyContainer(node2)//
        .asIterator();

    when(neo4JLowLevelAPIMock.getNodesOfType(SYSTEM_ENTITY_TYPE)).thenReturn(searchResult);

    @SuppressWarnings("unchecked")
    StorageIterator<TestSystemEntityWrapper> storageIterator = mock(StorageIterator.class);
    when(neo4jStorageIteratorFactoryMock.forNode(SYSTEM_ENTITY_TYPE, searchResult)).thenReturn(storageIterator);

    // action
    StorageIterator<TestSystemEntityWrapper> actualStorageIterator = instance.getEntities(SYSTEM_ENTITY_TYPE);

    // verify
    assertThat(actualStorageIterator, is(sameInstance(storageIterator)));
    verify(transactionMock).success();
  }

  @Test(expected = StorageException.class)
  public void getSystemEntitiesThrowsAStorageExceptionWhenTheIteratorCannotBeCreated() throws Exception {
    // setup
    Node node1 = aNode().build();
    ResourceIterator<Node> searchResult = aNodeSearchResult()//
        .withPropertyContainer(node1)//
        .andPropertyContainer(aNode().build())//
        .asIterator();
    when(neo4JLowLevelAPIMock.getNodesOfType(SYSTEM_ENTITY_TYPE)).thenReturn(searchResult);

    when(neo4jStorageIteratorFactoryMock.forNode(SYSTEM_ENTITY_TYPE, searchResult)).thenThrow(new StorageException());

    try {
      // action
      instance.getEntities(SYSTEM_ENTITY_TYPE);
    } finally {
      // verify
      verify(transactionMock).failure();
    }
  }

  @Test
  public void updateDomainEntityRetrievesTheNodeAndUpdatesItsValues() throws Exception {
    // setup
    Node nodeMock = aNode().withRevision(FIRST_REVISION).build();
    latestNodeFoundFor(DOMAIN_ENTITY_TYPE, ID, nodeMock);

    NodeConverter<SubADomainEntity> domainEntityConverterMock = propertyContainerConverterFactoryHasANodeConverterTypeFor(DOMAIN_ENTITY_TYPE);

    Change oldModified = CHANGE;
    SubADomainEntity domainEntity = aDomainEntity() //
        .withId(ID) //
        .withRev(SECOND_REVISION)//
        .withModified(oldModified)//
        .build();

    instance.updateEntity(DOMAIN_ENTITY_TYPE, domainEntity);

    // verify
    InOrder inOrder = inOrder(dbMock, domainEntityConverterMock, transactionMock);
    inOrder.verify(domainEntityConverterMock).updatePropertyContainer( //
        nodeMock, //
        domainEntity);
    inOrder.verify(domainEntityConverterMock).updateModifiedAndRev( //
        nodeMock, //
        domainEntity);
    inOrder.verify(transactionMock).success();
  }

  @Test(expected = UpdateException.class)
  public void updateDomainEntityThrowsAnUpdateExceptionWhenTheEntityCannotBeFound() throws Exception {
    // setup
    anEmptyNodeSearchResult().forLabel(DOMAIN_ENTITY_LABEL).andId(ID).foundInDB(dbMock);

    Change oldModified = CHANGE;
    SubADomainEntity domainEntity = aDomainEntity() //
        .withId(ID) //
        .withRev(FIRST_REVISION)//
        .withAPid()//
        .withModified(oldModified)//
        .build();

    try {
      // action
      instance.updateEntity(DOMAIN_ENTITY_TYPE, domainEntity);
    } finally {
      // verify
      verify(transactionMock).failure();
    }
  }

  @Test(expected = UpdateException.class)
  public void updateDomainEntityThrowsAnUpdateExceptionWhenRevOfTheNodeIsHigherThatnOfTheEntity() throws Exception {
    testUpdateDomainEntityRevUpdateException(FIRST_REVISION, FIRST_REVISION);
  }

  @Test(expected = UpdateException.class)
  public void updateDomainEntityThrowsAnUpdateExceptionWhenRevOfTheNodeIsEqualToThatOfTheEntity() throws Exception {
    testUpdateDomainEntityRevUpdateException(FIRST_REVISION, FIRST_REVISION);
  }

  @Test(expected = UpdateException.class)
  public void updateDomainEntityThrowsAnUpdateExceptionWhenRevOfTheNodeIsMoreThanOneLowerThanThatOfTheEntity() throws Exception {
    testUpdateDomainEntityRevUpdateException(THIRD_REVISION, FIRST_REVISION);
  }

  private void testUpdateDomainEntityRevUpdateException(int entityRev, int nodeRev) throws StorageException {
    // setup
    Node node = aNode().withRevision(nodeRev).build();
    latestNodeFoundFor(DOMAIN_ENTITY_TYPE, ID, node);

    Change oldModified = CHANGE;
    SubADomainEntity domainEntity = aDomainEntity() //
        .withId(ID) //
        .withRev(entityRev)//
        .withAPid()//
        .withModified(oldModified)//
        .build();

    try {
      // action
      instance.updateEntity(DOMAIN_ENTITY_TYPE, domainEntity);
    } finally {
      // verify
      verify(transactionMock).failure();
    }
  }

  @Test
  public void addVariantAddsANewVariantToTheExistingNodeOfTheBaseType() throws Exception {
    // setup
    SubADomainEntity entity = aDomainEntity().withId(ID).withRev(SECOND_REVISION).build();
    Node node = aNode().withId(ID).withRevision(FIRST_REVISION).build();
    when(neo4JLowLevelAPIMock.getLatestNodeById(PRIMITIVE_DOMAIN_ENTITY_TYPE, ID)).thenReturn(node);

    NodeConverter<SubADomainEntity> nodeConverterMock = propertyContainerConverterFactoryHasANodeConverterTypeFor(DOMAIN_ENTITY_TYPE);

    // action
    instance.addVariant(DOMAIN_ENTITY_TYPE, entity, CHANGE);

    // verify
    verify(nodeConverterMock).addValuesToPropertyContainer(node, entity);
    verify(nodeConverterMock).updateModifiedAndRev(node, entity);
    verify(transactionMock, atLeastOnce()).success();
  }

  @Test
  public void addVariantAddsUpdatesTheAdministrativeValuesBeforeAddingTheValuesToTheNode() throws Exception {
    // setup
    SubADomainEntity entity = aDomainEntity()//
        .withId(ID)//
        .withRev(SECOND_REVISION)//
        .build();
    Node node = aNode().withId(ID).withRevision(FIRST_REVISION).build();
    when(neo4JLowLevelAPIMock.getLatestNodeById(PRIMITIVE_DOMAIN_ENTITY_TYPE, ID)).thenReturn(node);

    NodeConverter<SubADomainEntity> nodeConverterMock = propertyContainerConverterFactoryHasANodeConverterTypeFor(DOMAIN_ENTITY_TYPE);

    // action
    instance.addVariant(DOMAIN_ENTITY_TYPE, entity, CHANGE);

    // verify
    verify(nodeConverterMock).addValuesToPropertyContainer(//
        node, //
        entity);
    verify(nodeConverterMock).updateModifiedAndRev(//
        node, //
        entity);
    verify(transactionMock, atLeastOnce()).success();
  }

  @Test(expected = NoSuchEntityException.class)
  public void addVariantThrowsANoSuchEntityExceptionWhenTheEntityDoesNotExist() throws Exception {
    // setup
    when(neo4JLowLevelAPIMock.getLatestNodeById(PRIMITIVE_DOMAIN_ENTITY_TYPE, ID)).thenReturn(null);

    try {
      // action
      instance.addVariant(DOMAIN_ENTITY_TYPE, aDomainEntity().build(), CHANGE);
    } finally {
      // verify
      verify(transactionMock).failure();
    }
  }

  @Test(expected = UpdateException.class)
  public void addVariantThrowsAnUpdateExceptionWhenRevisionIsHigherMoreThanOneTheTheRevisionOfTheNode() throws Exception {
    addVariantThrowsUpdateExceptionForRevisionMismatch(FIRST_REVISION, THIRD_REVISION);
  }

  @Test(expected = UpdateException.class)
  public void addVariantThrowsAnUpdateExceptionWhenRevisionIsEqualToTheRevisionOfTheNode() throws Exception {
    addVariantThrowsUpdateExceptionForRevisionMismatch(THIRD_REVISION, THIRD_REVISION);
  }

  @Test(expected = UpdateException.class)
  public void addVariantThrowsAnUpdateExceptionWhenRevisionIsLowerThanTheRevisionOfTheNode() throws Exception {
    addVariantThrowsUpdateExceptionForRevisionMismatch(THIRD_REVISION, SECOND_REVISION);
  }

  private void addVariantThrowsUpdateExceptionForRevisionMismatch(int nodeRev, int entityRev) throws StorageException {
    // setup
    SubADomainEntity entity = aDomainEntity().withId(ID).withRev(entityRev).build();
    Node node = aNode().withId(ID).withRevision(nodeRev).build();
    when(neo4JLowLevelAPIMock.getLatestNodeById(PRIMITIVE_DOMAIN_ENTITY_TYPE, ID)).thenReturn(node);

    try {
      // action
      instance.addVariant(DOMAIN_ENTITY_TYPE, entity, CHANGE);
    } finally {
      // verify
      verify(transactionMock).failure();
    }
  }

  @Test(expected = UpdateException.class)
  public void addVariantThrowsAnUpdateExceptionWhenTheEntityAlreadyContainsTheVariant() throws Exception {
    // setup
    SubADomainEntity entity = aDomainEntity().withId(ID).withRev(FIRST_REVISION).build();
    Node node = aNode().withId(ID).withLabel(DOMAIN_ENTITY_LABEL).withRevision(FIRST_REVISION).build();
    when(neo4JLowLevelAPIMock.getLatestNodeById(DOMAIN_ENTITY_TYPE, ID)).thenReturn(node);

    try {
      // action
      instance.addVariant(DOMAIN_ENTITY_TYPE, entity, CHANGE);
    } finally {
      // verify
      verify(transactionMock).failure();
    }
  }

  @Test
  public void updateSystemEntityRetrievesTheEntityAndUpdatesTheData() throws Exception {
    // setup
    Node nodeMock = aNode().withRevision(FIRST_REVISION).build();
    latestNodeFoundFor(SYSTEM_ENTITY_TYPE, ID, nodeMock);

    NodeConverter<TestSystemEntityWrapper> systemEntityConverterMock = propertyContainerConverterFactoryHasANodeConverterTypeFor(SYSTEM_ENTITY_TYPE);

    TestSystemEntityWrapper systemEntity = aSystemEntity() //
        .withId(ID)//
        .withRev(SECOND_REVISION)//
        .build();

    // action
    instance.updateEntity(SYSTEM_ENTITY_TYPE, systemEntity);

    // verify
    InOrder inOrder = inOrder(systemEntityConverterMock, transactionMock);
    inOrder.verify(systemEntityConverterMock).updatePropertyContainer( //
        nodeMock, //
        systemEntity);

    inOrder.verify(systemEntityConverterMock).updateModifiedAndRev( //
        nodeMock, //
        systemEntity);
    inOrder.verify(transactionMock).success();
  }

  @Test(expected = UpdateException.class)
  public void updateSystemEntityThrowsAnUpdateExceptionIfTheNodeIsNewerThanTheEntityWithTheUpdatedInformation() throws Exception {
    testUpdateSystemEntityRevisionUpdateException(SECOND_REVISION, SECOND_REVISION);
  }

  @Test(expected = UpdateException.class)
  public void updateSystemEntityThrowsAnUpdateExceptionIfTheNodeIsOlderThanTheEntityWithTheUpdatedInformation() throws Exception {
    testUpdateSystemEntityRevisionUpdateException(FIRST_REVISION, THIRD_REVISION);
  }

  private void testUpdateSystemEntityRevisionUpdateException(int nodeRev, int entityRevision) throws StorageException {
    // setup
    Node nodeWithNewerRevision = aNode().withRevision(nodeRev).build();
    latestNodeFoundFor(SYSTEM_ENTITY_TYPE, ID, nodeWithNewerRevision);

    TestSystemEntityWrapper systemEntity = aSystemEntity() //
        .withId(ID)//
        .withRev(entityRevision)//
        .build();

    try {
      // action
      instance.updateEntity(SYSTEM_ENTITY_TYPE, systemEntity);
    } finally {
      // verify
      verify(transactionMock).failure();
    }
  }

  @Test(expected = UpdateException.class)
  public void updateSystemEntityThrowsAnUpdateExceptionIfTheNodeCannotBeFound() throws Exception {
    // setup
    noLatestNodeFoundFor(SYSTEM_ENTITY_TYPE, ID);

    TestSystemEntityWrapper systemEntity = aSystemEntity() //
        .withId(ID)//
        .withRev(FIRST_REVISION).build();

    try {
      // action
      instance.updateEntity(SYSTEM_ENTITY_TYPE, systemEntity);
    } finally {
      // verify
      verify(transactionMock).failure();
    }
  }

  @Test(expected = ConversionException.class)
  public void updateSystemEntityThrowsAConversionExceptionWhenTheEntityConverterThrowsOne() throws Exception {
    // setup
    Node nodeMock = aNode().withRevision(FIRST_REVISION).build();
    latestNodeFoundFor(SYSTEM_ENTITY_TYPE, ID, nodeMock);

    NodeConverter<TestSystemEntityWrapper> systemEntityConverterMock = propertyContainerConverterFactoryHasANodeConverterTypeFor(SYSTEM_ENTITY_TYPE);

    Change oldModified = new Change();
    TestSystemEntityWrapper systemEntity = aSystemEntity() //
        .withId(ID)//
        .withRev(SECOND_REVISION)//
        .withModified(oldModified)//
        .build();

    doThrow(ConversionException.class).when(systemEntityConverterMock).updatePropertyContainer(nodeMock, systemEntity);

    try {
      // action
      instance.updateEntity(SYSTEM_ENTITY_TYPE, systemEntity);
    } finally {
      // verify
      verify(transactionMock).failure();
    }
  }

  @Test
  public void deleteDomainEntityFirstRemovesTheNodesRelationShipsAndThenTheNodeItselfTheDatabase() throws Exception {
    // setup
    Relationship relMock1 = aRelationship().build();
    Relationship relMock2 = aRelationship().build();
    Node nodeMock = aNode().withOutgoingRelationShip(relMock1).andOutgoingRelationship(relMock2).build();

    nodesFoundFor(PRIMITIVE_DOMAIN_ENTITY_TYPE, ID, nodeMock);

    // action
    instance.deleteDomainEntity(PRIMITIVE_DOMAIN_ENTITY_TYPE, ID, CHANGE);

    // verify
    InOrder inOrder = inOrder(dbMock, nodeMock, relMock1, relMock2, transactionMock);
    inOrder.verify(dbMock).beginTx();
    verifyNodeAndItsRelationAreDelete(nodeMock, relMock1, relMock2, inOrder);
    inOrder.verify(transactionMock).success();

  }

  private void nodesFoundFor(Class<? extends Entity> type, String id, Node... foundNodes) {
    when(neo4JLowLevelAPIMock.getNodesWithId(type, id)).thenReturn(Lists.newArrayList(foundNodes));
  }

  private void noNodesFoundFor(Class<? extends Entity> type, String id) {
    List<Node> nodes = Lists.newArrayList();
    when(neo4JLowLevelAPIMock.getNodesWithId(type, id)).thenReturn(nodes);
  }

  @Test
  public void deleteDomainEntityRemovesAllTheFoundNodes() throws Exception {
    // setup
    Relationship relMock1 = aRelationship().build();
    Relationship relMock2 = aRelationship().build();
    Node nodeMock = aNode().withOutgoingRelationShip(relMock1).andOutgoingRelationship(relMock2).build();

    Relationship relMock3 = aRelationship().build();
    Relationship relMock4 = aRelationship().build();
    Node nodeMock2 = aNode().withOutgoingRelationShip(relMock3).andOutgoingRelationship(relMock4).build();

    Relationship relMock5 = aRelationship().build();
    Relationship relMock6 = aRelationship().build();
    Node nodeMock3 = aNode().withOutgoingRelationShip(relMock5).andOutgoingRelationship(relMock6).build();

    nodesFoundFor(PRIMITIVE_DOMAIN_ENTITY_TYPE, ID, nodeMock, nodeMock2, nodeMock3);

    // action
    instance.deleteDomainEntity(PRIMITIVE_DOMAIN_ENTITY_TYPE, ID, CHANGE);

    // verify
    InOrder inOrder = inOrder(dbMock, nodeMock, relMock1, relMock2, nodeMock2, relMock3, relMock4, nodeMock3, relMock5, relMock6, transactionMock);
    verifyNodeAndItsRelationAreDelete(nodeMock, relMock1, relMock2, inOrder);
    verifyNodeAndItsRelationAreDelete(nodeMock2, relMock3, relMock4, inOrder);
    verifyNodeAndItsRelationAreDelete(nodeMock3, relMock5, relMock6, inOrder);
    inOrder.verify(transactionMock).success();
  }

  private void verifyNodeAndItsRelationAreDelete(Node node, Relationship relMock1, Relationship relMock2, InOrder inOrder) {
    inOrder.verify(node).getRelationships();
    inOrder.verify(relMock1).delete();
    inOrder.verify(relMock2).delete();
    inOrder.verify(node).delete();
  }

  @Test(expected = NoSuchEntityException.class)
  public void deleteDomainEntityThrowsANoSuchEntityExceptionWhenTheEntityCannotBeFound() throws Exception {
    // setup
    noNodesFoundFor(PRIMITIVE_DOMAIN_ENTITY_TYPE, ID);
    try {
      // action
      instance.deleteDomainEntity(PRIMITIVE_DOMAIN_ENTITY_TYPE, ID, CHANGE);
    } finally {
      // verify
      verify(transactionMock).failure();
    }
  }

  @Test(expected = IllegalArgumentException.class)
  public void deleteThrowsAnIllegalArgumentExceptionWhenTheEntityIsNotAPrimitiveDomainEntity() throws Exception {

    try {
      // action
      instance.deleteDomainEntity(DOMAIN_ENTITY_TYPE, ID, CHANGE);
    } finally {
      // verify
      verifyZeroInteractions(dbMock);
    }
  }

  @Test
  public void deleteSystemEntityFirstRemovesTheNodesRelationShipsAndThenTheNodeItselfTheDatabase() throws Exception {
    // setup
    Relationship relMock1 = aRelationship().build();
    Relationship relMock2 = aRelationship().build();
    Node nodeMock = aNode().withOutgoingRelationShip(relMock1).andOutgoingRelationship(relMock2).build();

    nodesFoundFor(SYSTEM_ENTITY_TYPE, ID, nodeMock);

    // action
    int numDeleted = instance.deleteSystemEntity(SYSTEM_ENTITY_TYPE, ID);

    // verify
    assertThat(numDeleted, is(equalTo(1)));
    InOrder inOrder = inOrder(dbMock, nodeMock, relMock1, relMock2, transactionMock);
    inOrder.verify(dbMock).beginTx();
    verifyNodeAndItsRelationAreDelete(nodeMock, relMock1, relMock2, inOrder);
    inOrder.verify(transactionMock).success();

  }

  @Test
  public void deleteSystemEntityReturns0WhenTheEntityCannotBeFound() throws Exception {
    // setup
    noNodesFoundFor(SYSTEM_ENTITY_TYPE, ID);

    // action
    int numDeleted = instance.deleteSystemEntity(SYSTEM_ENTITY_TYPE, ID);
    // verify
    assertThat(numDeleted, is(equalTo(0)));
    verify(transactionMock).success();
  }

  @Test
  public void getDomainEntityRevisionReturnsTheDomainEntityWithTheRequestedRevision() throws Exception {
    Node nodeWithSameRevision = aNode().withRevision(FIRST_REVISION).withAPID().build();
    nodeWithRevisionFound(DOMAIN_ENTITY_TYPE, ID, FIRST_REVISION, nodeWithSameRevision);

    NodeConverter<SubADomainEntity> converter = propertyContainerConverterFactoryHasANodeConverterTypeFor(DOMAIN_ENTITY_TYPE);
    when(converter.convertToEntity(nodeWithSameRevision)).thenReturn(aDomainEntity().withAPid().build());

    // action
    SubADomainEntity entity = instance.getDomainEntityRevision(DOMAIN_ENTITY_TYPE, ID, FIRST_REVISION);

    // verify
    assertThat(entity, is(instanceOf(SubADomainEntity.class)));
    verify(converter).convertToEntity(nodeWithSameRevision);
    verify(transactionMock).success();
  }

  private void nodeWithRevisionFound(Class<SubADomainEntity> type, String id, int revision, Node node) {
    when(neo4JLowLevelAPIMock.getNodeWithRevision(type, id, revision)).thenReturn(node);
  }

  private void noNodeWithRevisionFound(Class<SubADomainEntity> type, String id, int revision) {
    when(neo4JLowLevelAPIMock.getNodeWithRevision(type, id, revision)).thenReturn(null);
  }

  @Test
  public void getDomainEntityRevisionReturnsNullIfTheFoundEntityHasNoPID() throws Exception {
    Node nodeWithoutPID = aNode().withRevision(FIRST_REVISION).build();
    nodeWithRevisionFound(DOMAIN_ENTITY_TYPE, ID, FIRST_REVISION, nodeWithoutPID);

    NodeConverter<SubADomainEntity> nodeConverter = propertyContainerConverterFactoryHasANodeConverterTypeFor(DOMAIN_ENTITY_TYPE);
    when(nodeConverter.convertToEntity(nodeWithoutPID)).thenReturn(aDomainEntity().build());

    // action
    SubADomainEntity actualEntity = instance.getDomainEntityRevision(DOMAIN_ENTITY_TYPE, ID, FIRST_REVISION);

    // verify
    assertThat(actualEntity, is(nullValue()));
    verify(transactionMock).success();
  }

  @Test
  public void getDomainEntityRevisionReturnsNullIfTheEntityCannotBeFound() throws Exception {
    // setup
    noNodeWithRevisionFound(DOMAIN_ENTITY_TYPE, ID, FIRST_REVISION);

    // action
    SubADomainEntity entity = instance.getDomainEntityRevision(DOMAIN_ENTITY_TYPE, ID, FIRST_REVISION);

    // verify
    assertThat(entity, is(nullValue()));
    verify(transactionMock).success();
  }

  @Test
  public void getDomainEntityRevisionReturnsNullIfTheRevisionCannotBeFound() throws Exception {
    // setup
    Node nodeWithDifferentRevision = aNode().withRevision(SECOND_REVISION).withAPID().build();
    nodeWithRevisionFound(DOMAIN_ENTITY_TYPE, ID, SECOND_REVISION, nodeWithDifferentRevision);

    // action
    SubADomainEntity entity = instance.getDomainEntityRevision(DOMAIN_ENTITY_TYPE, ID, FIRST_REVISION);

    // verify
    assertThat(entity, is(nullValue()));
    verify(transactionMock).success();
  }

  @Test(expected = StorageException.class)
  public void getDomainEntityRevisionThrowsAStorageExceptionIfTheEntityCannotBeInstantiated() throws Exception {
    // setup
    Node nodeWithSameRevision = aNode().withRevision(FIRST_REVISION).withAPID().build();
    nodeWithRevisionFound(DOMAIN_ENTITY_TYPE, ID, FIRST_REVISION, nodeWithSameRevision);

    NodeConverter<SubADomainEntity> converter = propertyContainerConverterFactoryHasANodeConverterTypeFor(DOMAIN_ENTITY_TYPE);
    when(converter.convertToEntity(nodeWithSameRevision)).thenThrow(new InstantiationException());

    try {
      // action
      instance.getDomainEntityRevision(DOMAIN_ENTITY_TYPE, ID, FIRST_REVISION);
    } finally {
      // verify
      verify(transactionMock).failure();
    }
  }

  @Test(expected = ConversionException.class)
  public void getDomainEntityRevisionThrowsAConversionExceptionIfTheEntityCannotBeConverted() throws Exception {
    Node nodeWithSameRevision = aNode().withRevision(FIRST_REVISION).withAPID().build();
    nodeWithRevisionFound(DOMAIN_ENTITY_TYPE, ID, FIRST_REVISION, nodeWithSameRevision);

    NodeConverter<SubADomainEntity> converter = propertyContainerConverterFactoryHasANodeConverterTypeFor(DOMAIN_ENTITY_TYPE);
    when(converter.convertToEntity(nodeWithSameRevision)).thenThrow(new ConversionException());

    try {
      // action
      instance.getDomainEntityRevision(DOMAIN_ENTITY_TYPE, ID, FIRST_REVISION);
    } finally {
      // verify
      verify(converter).convertToEntity(nodeWithSameRevision);
      verify(transactionMock).failure();
    }
  }

  @Test
  public void getAllVariationsReturnsAllVariationsOfANode() throws Exception {
    // setup
    Node node = aNode()//
        .withLabel(PRIMITIVE_DOMAIN_ENTITY_LABEL)//
        .withLabel(DOMAIN_ENTITY_LABEL)//
        .build();

    when(neo4JLowLevelAPIMock.getLatestNodeById(PRIMITIVE_DOMAIN_ENTITY_TYPE, ID)).thenReturn(node);

    BaseDomainEntity primitive = new BaseDomainEntity();
    NodeConverter<BaseDomainEntity> primitiveConverter = propertyContainerConverterFactoryHasANodeConverterTypeFor(PRIMITIVE_DOMAIN_ENTITY_TYPE);
    when(primitiveConverter.convertToEntity(node)).thenReturn(primitive);

    SubADomainEntity domainEntity = aDomainEntity().build();
    NodeConverter<SubADomainEntity> converter = propertyContainerConverterFactoryHasANodeConverterTypeFor(DOMAIN_ENTITY_TYPE);
    when(converter.convertToEntity(node)).thenReturn(domainEntity);

    // action
    List<BaseDomainEntity> allVariations = instance.getAllVariations(PRIMITIVE_DOMAIN_ENTITY_TYPE, ID);

    // verify
    assertThat(allVariations, containsInAnyOrder(domainEntity, primitive));
    verifyTransactionSucceeded();

  }

  @Rule
  public ExpectedException thrown = ExpectedException.none();

  @Test()
  public void getAllVariationsThrowsAnIllegalArgumentExceptionWhenTheTypeIsNotAPrimitive() throws Exception {
    // setup
    thrown.expect(IllegalArgumentException.class);
    thrown.expectMessage("Nonprimitive type");
    thrown.expectMessage("" + DOMAIN_ENTITY_TYPE);

    // action
    instance.getAllVariations(DOMAIN_ENTITY_TYPE, ID);
  }

  @Test(expected = ConversionException.class)
  public void getAllVariationsThrowsAConversionExceptionWhenTheNodeCouldNotBeConverted() throws Exception {
    // setup
    Node node = aNode()//
        .withLabel(PRIMITIVE_DOMAIN_ENTITY_LABEL)//
        .withLabel(DOMAIN_ENTITY_LABEL)//
        .build();

    when(neo4JLowLevelAPIMock.getLatestNodeById(PRIMITIVE_DOMAIN_ENTITY_TYPE, ID)).thenReturn(node);

    NodeConverter<BaseDomainEntity> primitiveConverter = propertyContainerConverterFactoryHasANodeConverterTypeFor(PRIMITIVE_DOMAIN_ENTITY_TYPE);
    when(primitiveConverter.convertToEntity(node)).thenThrow(new ConversionException());
    propertyContainerConverterFactoryHasANodeConverterTypeFor(DOMAIN_ENTITY_TYPE);

    try {
      instance.getAllVariations(PRIMITIVE_DOMAIN_ENTITY_TYPE, ID);
    } finally {
      verifyTransactionFailed();
    }
  }

  @Test(expected = StorageException.class)
  public void getAllVariationsThrowsAStorageExceptionWhenTheEntityCouldNotInstantiated() throws Exception {
    // setup
    Node node = aNode()//
        .withLabel(PRIMITIVE_DOMAIN_ENTITY_LABEL)//
        .withLabel(DOMAIN_ENTITY_LABEL)//
        .build();

    when(neo4JLowLevelAPIMock.getLatestNodeById(PRIMITIVE_DOMAIN_ENTITY_TYPE, ID)).thenReturn(node);

    NodeConverter<BaseDomainEntity> primitiveConverter = propertyContainerConverterFactoryHasANodeConverterTypeFor(PRIMITIVE_DOMAIN_ENTITY_TYPE);
    when(primitiveConverter.convertToEntity(node)).thenThrow(new InstantiationException());
    propertyContainerConverterFactoryHasANodeConverterTypeFor(DOMAIN_ENTITY_TYPE);
    try {
      instance.getAllVariations(PRIMITIVE_DOMAIN_ENTITY_TYPE, ID);
    } finally {
      verifyTransactionFailed();
    }
  }

  private void setupEntityConverterFactory() throws Exception {
    propertyContainerConverterFactoryMock = mock(PropertyContainerConverterFactory.class);
  }

  private <T extends Entity> NodeConverter<T> propertyContainerConverterFactoryHasANodeConverterTypeFor(Class<T> type) {
    @SuppressWarnings("unchecked")
    NodeConverter<T> nodeConverter = mock(NodeConverter.class);
    when(propertyContainerConverterFactoryMock.createForType(argThat(equalTo(type)))).thenReturn(nodeConverter);
    return nodeConverter;
  }

  @Test
  public void setDomainEntityPIDAddsAPIDToTheLatestNodeIfMultipleAreFound() throws InstantiationException, IllegalAccessException, Exception {
    // setup
    Node nodeWithLatestRevision = aNode().withRevision(SECOND_REVISION).build();
    latestNodeFoundFor(DOMAIN_ENTITY_TYPE, ID, nodeWithLatestRevision);

    NodeConverter<SubADomainEntity> converterMock = propertyContainerConverterFactoryHasANodeConverterTypeFor(DOMAIN_ENTITY_TYPE);
    when(converterMock.convertToEntity(nodeWithLatestRevision)).thenReturn(aDomainEntity().withId(ID).build());

    // action
    instance.setDomainEntityPID(DOMAIN_ENTITY_TYPE, ID, PID);

    // verify
    verify(converterMock).addValuesToPropertyContainer( //
        argThat(equalTo(nodeWithLatestRevision)), //
        argThat(likeDomainEntity(DOMAIN_ENTITY_TYPE).withId(ID).withPID(PID)));

  }

  @Test
  public void setDomainEntityPIDAddsAPIDToTheNodeAndDuplicatesTheNode() throws InstantiationException, IllegalAccessException, Exception {
    // setup
    Node node = aNode().build();
    latestNodeFoundFor(DOMAIN_ENTITY_TYPE, ID, node);

    NodeConverter<SubADomainEntity> converterMock = propertyContainerConverterFactoryHasANodeConverterTypeFor(DOMAIN_ENTITY_TYPE);
    when(converterMock.convertToEntity(node)).thenReturn(aDomainEntity().withId(ID).build());

    // action
    instance.setDomainEntityPID(DOMAIN_ENTITY_TYPE, ID, PID);

    // verify
    verify(nodeDuplicatorMock).saveDuplicate(node);
    verify(transactionMock).success();
  }

  @Test(expected = IllegalStateException.class)
  public void setDomainEntityPIDThrowsAnIllegalStateExceptionWhenTheEntityAlreadyHasAPID() throws Exception {
    // setup
    Node aNodeWithAPID = aNode().withAPID().build();
    latestNodeFoundFor(DOMAIN_ENTITY_TYPE, ID, aNodeWithAPID);

    SubADomainEntity entityWithPID = aDomainEntity().withAPid().build();

    NodeConverter<SubADomainEntity> nodeConverter = propertyContainerConverterFactoryHasANodeConverterTypeFor(DOMAIN_ENTITY_TYPE);
    when(nodeConverter.convertToEntity(aNodeWithAPID)).thenReturn(entityWithPID);

    try {
      // action
      instance.setDomainEntityPID(DOMAIN_ENTITY_TYPE, ID, PID);
    } finally {
      // verify
      verify(transactionMock).failure();
    }
  }

  @Test(expected = ConversionException.class)
  public void setDomainEntityPIDThrowsAConversionExceptionWhenTheNodeCannotBeConverted() throws Exception {
    // setup
    Node aNode = aNode().build();
    latestNodeFoundFor(DOMAIN_ENTITY_TYPE, ID, aNode);

    NodeConverter<SubADomainEntity> nodeConverter = propertyContainerConverterFactoryHasANodeConverterTypeFor(DOMAIN_ENTITY_TYPE);
    when(nodeConverter.convertToEntity(aNode)).thenThrow(new ConversionException());

    try {
      // action
      instance.setDomainEntityPID(DOMAIN_ENTITY_TYPE, ID, PID);
    } finally {
      // verify
      verify(transactionMock).failure();
    }
  }

  @Test(expected = ConversionException.class)
  public void setDomainEntityPIDThrowsAConversionsExceptionWhenTheUpdatedEntityCannotBeCovnverted() throws Exception {
    // setup
    Node aNode = aNode().build();
    latestNodeFoundFor(DOMAIN_ENTITY_TYPE, ID, aNode);

    SubADomainEntity entity = aDomainEntity().build();

    NodeConverter<SubADomainEntity> nodeConverter = propertyContainerConverterFactoryHasANodeConverterTypeFor(DOMAIN_ENTITY_TYPE);
    when(nodeConverter.convertToEntity(aNode)).thenReturn(entity);
    doThrow(ConversionException.class).when(nodeConverter).addValuesToPropertyContainer(aNode, entity);

    try {
      // action
      instance.setDomainEntityPID(DOMAIN_ENTITY_TYPE, ID, PID);
    } finally {
      // verify
      verify(nodeConverter).addValuesToPropertyContainer(aNode, entity);
      verify(transactionMock).failure();
    }
  }

  @Test(expected = StorageException.class)
  public void setDomainEntityPIDThrowsAStorageExceptionWhenTheEntityDoesNotExist() throws Exception {
    // setup
    noLatestNodeFoundFor(DOMAIN_ENTITY_TYPE, ID);

    try {
      // action
      instance.setDomainEntityPID(DOMAIN_ENTITY_TYPE, ID, PID);
    } finally {
      // verify
      verify(transactionMock).failure();
    }

  }

  @Test(expected = StorageException.class)
  public void setDomainEntityPIDThrowsAStorageExceptionWhenTheEntityCannotBeInstatiated() throws Exception {
    // setup
    Node aNode = aNode().build();
    latestNodeFoundFor(DOMAIN_ENTITY_TYPE, ID, aNode);

    NodeConverter<SubADomainEntity> converterMock = propertyContainerConverterFactoryHasANodeConverterTypeFor(DOMAIN_ENTITY_TYPE);
    when(converterMock.convertToEntity(aNode)).thenThrow(new InstantiationException());

    try {
      // action
      instance.setDomainEntityPID(DOMAIN_ENTITY_TYPE, ID, PID);
    } finally {
      // verify
      verify(transactionMock).failure();
    }
  }

  @Test
  public void findEntityByPropertyConvertsTheFirstNodeFoundWithProperty() throws Exception {
    // setup
    Node node = aNode().build();
    nodeFoundFor(DOMAIN_ENTITY_TYPE, node, PROPERTY_NAME, PROPERTY_VALUE);

    NodeConverter<SubADomainEntity> converterMock = propertyContainerConverterFactoryHasANodeConverterTypeFor(DOMAIN_ENTITY_TYPE);
    when(converterMock.getPropertyName(DOMAIN_ENTITY_FIELD)).thenReturn(PROPERTY_NAME);
    SubADomainEntity entity = aDomainEntity().build();
    when(converterMock.convertToEntity(node)).thenReturn(entity);

    // action
    SubADomainEntity actualEntity = instance.findEntityByProperty(DOMAIN_ENTITY_TYPE, DOMAIN_ENTITY_FIELD, PROPERTY_VALUE);

    // verify
    assertThat(actualEntity, is(sameInstance(entity)));

    verifyTransactionSucceeded();
  }

  @Test
  public void findEntityByPropertyReturnsNullIfNoNodeIsFound() throws Exception {
    // setup
    noNodeFoundFor(DOMAIN_ENTITY_TYPE, PROPERTY_NAME, PROPERTY_VALUE);

    NodeConverter<SubADomainEntity> converterMock = propertyContainerConverterFactoryHasANodeConverterTypeFor(DOMAIN_ENTITY_TYPE);
    when(converterMock.getPropertyName(DOMAIN_ENTITY_FIELD)).thenReturn(PROPERTY_NAME);

    // action
    SubADomainEntity actualEntity = instance.findEntityByProperty(DOMAIN_ENTITY_TYPE, DOMAIN_ENTITY_FIELD, PROPERTY_VALUE);

    // verify
    assertThat(actualEntity, is(nullValue()));

    verifyTransactionSucceeded();
  }

  private void noNodeFoundFor(Class<SubADomainEntity> type, String propertyName, String propertyValue) {
    when(neo4JLowLevelAPIMock.findNodeByProperty(type, propertyName, propertyValue)).thenReturn(null);
  }

  @Test(expected = ConversionException.class)
  public void findEntityByPropertyThrowsAConversionExceptionWhenTheNodeCannotBeConverted() throws Exception {
    // setup
    Node node = aNode().build();
    nodeFoundFor(DOMAIN_ENTITY_TYPE, node, PROPERTY_NAME, PROPERTY_VALUE);

    NodeConverter<SubADomainEntity> converterMock = propertyContainerConverterFactoryHasANodeConverterTypeFor(DOMAIN_ENTITY_TYPE);
    when(converterMock.getPropertyName(DOMAIN_ENTITY_FIELD)).thenReturn(PROPERTY_NAME);
    when(converterMock.convertToEntity(node)).thenThrow(new ConversionException());

    try {
      // action
      instance.findEntityByProperty(DOMAIN_ENTITY_TYPE, DOMAIN_ENTITY_FIELD, PROPERTY_VALUE);
    } finally {
      // verify
      verifyTransactionFailed();
    }
  }

  @Test(expected = StorageException.class)
  public void findEntityByPropertyThrowsAStorageExceptionWhenTheEntityCannotBeInstantiated() throws Exception {
    // setup
    Node node = aNode().build();
    nodeFoundFor(DOMAIN_ENTITY_TYPE, node, PROPERTY_NAME, PROPERTY_VALUE);

    NodeConverter<SubADomainEntity> converterMock = propertyContainerConverterFactoryHasANodeConverterTypeFor(DOMAIN_ENTITY_TYPE);
    when(converterMock.getPropertyName(DOMAIN_ENTITY_FIELD)).thenReturn(PROPERTY_NAME);
    when(converterMock.convertToEntity(node)).thenThrow(new InstantiationException());

    try {
      // action
      instance.findEntityByProperty(DOMAIN_ENTITY_TYPE, DOMAIN_ENTITY_FIELD, PROPERTY_VALUE);
    } finally {
      // verify
      verifyTransactionFailed();
    }
  }

  private void nodeFoundFor(Class<SubADomainEntity> type, Node node, String propertyName, String propertyValue) {
    when(neo4JLowLevelAPIMock.findNodeByProperty(type, propertyName, propertyValue)).thenReturn(node);
  }

  @Test
  public void countEntitiesDelegatesToNeo4JLowLevelAPICountNodesWithLabel() {
    // setup
    long count = 5l;
    when(neo4JLowLevelAPIMock.countNodesWithLabel(SYSTEM_ENTITY_LABEL)).thenReturn(count);

    // action
    long actualCount = instance.countEntities(SYSTEM_ENTITY_TYPE);

    // verify
    assertThat(actualCount, is(equalTo(count)));
  }

  @Test
  public void countEntitiesUsesThePrimitiveDomainEntity() {
    // setup
    long count = 5l;
    when(neo4JLowLevelAPIMock.countNodesWithLabel(PRIMITIVE_DOMAIN_ENTITY_LABEL)).thenReturn(count);

    // action
    long actualCount = instance.countEntities(DOMAIN_ENTITY_TYPE);

    // verify
    assertThat(actualCount, is(equalTo(count)));
  }

  @Test
  public void entityExistsTriesToRetrieveTheNodeWithTheIdAndReturnsTrueIfTheNodeExists() {
    // setup
    Node aNode = aNode().build();
    when(neo4JLowLevelAPIMock.getLatestNodeById(DOMAIN_ENTITY_TYPE, ID)).thenReturn(aNode);

    // action
    boolean exists = instance.entityExists(DOMAIN_ENTITY_TYPE, ID);

    // verify
    assertThat(exists, is(true));
    verifyTransactionSucceeded();
  }

  @Test
  public void entityExistsTriesToRetrieveTheNodeWithTheIdAndReturnsFalseIfTheNodeExists() {
    // setup
    when(neo4JLowLevelAPIMock.getLatestNodeById(DOMAIN_ENTITY_TYPE, ID)).thenReturn(null);

    // action
    boolean exists = instance.entityExists(DOMAIN_ENTITY_TYPE, ID);

    // verify
    assertThat(exists, is(false));
    verifyTransactionSucceeded();
  }

  @Test
  public void getIdsOfNonPersistentDomainEntitiesFiltersTheIdsOfGetNodesOfType() {
    // setup
    Node nodeWithAPID = aNode().withId(ID).withAPID().build();
    String id2 = "id2";
    Node nodeWithoutAPID = aNode().withId(id2).build();
    ResourceIterator<Node> foundNodes = aNodeSearchResult().withPropertyContainer(nodeWithoutAPID).andPropertyContainer(nodeWithAPID).asIterator();
    when(neo4JLowLevelAPIMock.getNodesOfType(DOMAIN_ENTITY_TYPE)).thenReturn(foundNodes);

    // action
    List<String> ids = instance.getIdsOfNonPersistentDomainEntities(DOMAIN_ENTITY_TYPE);

    // verify
    assertThat(ids, contains(id2));
    assertThat(ids, not(contains(ID)));

    verify(transactionMock).success();
  }

  /* *****************************************************************************
   * Relation
   * *****************************************************************************/

  @Test
  public void addRelationAddsARelationshipToTheSource() throws Exception {
    // setup
    String name = "regularTypeName";

    Node sourceNodeMock = aNode().build();
    latestNodeFoundFor(PRIMITIVE_DOMAIN_ENTITY_TYPE, RELATION_SOURCE_ID, sourceNodeMock);

    Node targetNodeMock = aNode().build();
    latestNodeFoundFor(PRIMITIVE_DOMAIN_ENTITY_TYPE, RELATION_TARGET_ID, targetNodeMock);

    relationTypeWithRegularNameExists(name);

    Relationship relationShipMock = mock(RELATIONSHIP_TYPE);

    RelationshipConverter<SubARelation> relationConverterMock = propertyContainerFactoryHasCompositeRelationshipConverterFor(RELATION_TYPE);

    when(sourceNodeMock.createRelationshipTo(argThat(equalTo(targetNodeMock)), argThat(likeRelationshipType().withName(name)))).thenReturn(relationShipMock);
    when(idGeneratorMock.nextIdFor(RELATION_TYPE)).thenReturn(ID);
    SubARelation relation = aRelation()//
        .withId(ID)//
        .withSourceId(RELATION_SOURCE_ID) //
        .withSourceType(PRIMITIVE_DOMAIN_ENTITY_NAME) //
        .withTargetId(RELATION_TARGET_ID) //
        .withTargetType(PRIMITIVE_DOMAIN_ENTITY_NAME) //
        .withTypeId(RELATION_TYPE_ID) //
        .withTypeType(RELATION_TYPE_NAME).build();

    // action
    instance.addRelation(RELATION_TYPE, relation, new Change());

    // verify
    InOrder inOrder = inOrder(dbMock, sourceNodeMock, relationConverterMock, transactionMock, neo4JLowLevelAPIMock);

    inOrder.verify(dbMock).beginTx();
    inOrder.verify(sourceNodeMock).createRelationshipTo(argThat(equalTo(targetNodeMock)), argThat(likeRelationshipType().withName(name)));

    inOrder.verify(relationConverterMock).addValuesToPropertyContainer( //
        relationShipMock, //
        relation);
    inOrder.verify(neo4JLowLevelAPIMock).addRelationship(relationShipMock, ID);
    inOrder.verify(transactionMock).success();
  }

  @Test(expected = StorageException.class)
  public void addRelationThrowsAConversionExceptionWhenTheRelationshipConverterDoes() throws Exception {
    SubARelation relation = aRelation()//
        .withSourceId(RELATION_SOURCE_ID) //
        .withSourceType(PRIMITIVE_DOMAIN_ENTITY_NAME) //
        .withTargetId(RELATION_TARGET_ID) //
        .withTargetType(PRIMITIVE_DOMAIN_ENTITY_NAME) //
        .withTypeId(RELATION_TYPE_ID) //
        .withTypeType(RELATION_TYPE_NAME).build();
    String name = "regularTypeName";

    Node sourceNodeMock = aNode().build();
    latestNodeFoundFor(PRIMITIVE_DOMAIN_ENTITY_TYPE, RELATION_SOURCE_ID, sourceNodeMock);

    Node targetNodeMock = aNode().build();
    latestNodeFoundFor(PRIMITIVE_DOMAIN_ENTITY_TYPE, RELATION_TARGET_ID, targetNodeMock);

    relationTypeWithRegularNameExists(name);

    Relationship relationShipMock = mock(RELATIONSHIP_TYPE);

    RelationshipConverter<SubARelation> relationConverterMock = propertyContainerFactoryHasCompositeRelationshipConverterFor(RELATION_TYPE);

    when(sourceNodeMock.createRelationshipTo(argThat(equalTo(targetNodeMock)), argThat(likeRelationshipType().withName(name)))).thenReturn(relationShipMock);
    when(idGeneratorMock.nextIdFor(RELATION_TYPE)).thenReturn(ID);
    doThrow(ConversionException.class).when(relationConverterMock).addValuesToPropertyContainer(relationShipMock, relation);

    try {
      // action
      instance.addRelation(RELATION_TYPE, relation, new Change());
    } finally {
      // verify
      verify(dbMock).beginTx();
      verify(sourceNodeMock).createRelationshipTo(argThat(equalTo(targetNodeMock)), argThat(likeRelationshipType().withName(name)));

      verify(relationConverterMock).addValuesToPropertyContainer( //
          relationShipMock, //
          relation);
      verifyTransactionFailed();
    }
  }

  @Test(expected = StorageException.class)
  public void addRelationThrowsAStorageExceptionWhenTheRelationTypeCannotBeInstantiated() throws Exception {
    SubARelation relation = aRelation()//
        .withSourceId(RELATION_SOURCE_ID) //
        .withSourceType(PRIMITIVE_DOMAIN_ENTITY_NAME) //
        .withTargetId(RELATION_TARGET_ID) //
        .withTargetType(PRIMITIVE_DOMAIN_ENTITY_NAME) //
        .withTypeId(RELATION_TYPE_ID) //
        .withTypeType(RELATION_TYPE_NAME).build();
    String name = "regularTypeName";

    Node sourceNodeMock = aNode().build();
    latestNodeFoundFor(PRIMITIVE_DOMAIN_ENTITY_TYPE, RELATION_SOURCE_ID, sourceNodeMock);

    Node targetNodeMock = aNode().build();
    latestNodeFoundFor(PRIMITIVE_DOMAIN_ENTITY_TYPE, RELATION_TARGET_ID, targetNodeMock);

    NodeConverter<RelationType> relationTypeConverter = relationTypeWithRegularNameExists(name);

    Relationship relationShipMock = mock(RELATIONSHIP_TYPE);

    when(sourceNodeMock.createRelationshipTo(argThat(equalTo(targetNodeMock)), argThat(likeRelationshipType().withName(name)))).thenReturn(relationShipMock);
    when(idGeneratorMock.nextIdFor(RELATION_TYPE)).thenReturn(ID);
    when(relationTypeConverter.convertToEntity(any(Node.class))).thenThrow(new InstantiationException());

    try {
      // action
      instance.addRelation(RELATION_TYPE, relation, new Change());
    } finally {
      // verify
      verifyTransactionFailed();
    }
  }

  @Test(expected = ConversionException.class)
  public void addRelationThrowsAConversionExceptionWhenTheRelationCannotBeConverted() throws Exception {
    SubARelation relation = aRelation()//
        .withSourceId(RELATION_SOURCE_ID) //
        .withSourceType(PRIMITIVE_DOMAIN_ENTITY_NAME) //
        .withTargetId(RELATION_TARGET_ID) //
        .withTargetType(PRIMITIVE_DOMAIN_ENTITY_NAME) //
        .withTypeId(RELATION_TYPE_ID) //
        .withTypeType(RELATION_TYPE_NAME).build();
    String name = "regularTypeName";

    Node sourceNodeMock = aNode().build();
    latestNodeFoundFor(PRIMITIVE_DOMAIN_ENTITY_TYPE, RELATION_SOURCE_ID, sourceNodeMock);

    Node targetNodeMock = aNode().build();
    latestNodeFoundFor(PRIMITIVE_DOMAIN_ENTITY_TYPE, RELATION_TARGET_ID, targetNodeMock);

    NodeConverter<RelationType> relationTypeConverter = relationTypeWithRegularNameExists(name);

    Relationship relationShipMock = mock(RELATIONSHIP_TYPE);

    RelationshipConverter<SubARelation> relationConverterMock = propertyContainerFactoryHasCompositeRelationshipConverterFor(RELATION_TYPE);

    when(sourceNodeMock.createRelationshipTo(argThat(equalTo(targetNodeMock)), argThat(likeRelationshipType().withName(name)))).thenReturn(relationShipMock);
    when(idGeneratorMock.nextIdFor(RELATION_TYPE)).thenReturn(ID);
    when(relationTypeConverter.convertToEntity(any(Node.class))).thenThrow(new ConversionException());

    try {
      // action
      instance.addRelation(RELATION_TYPE, relation, new Change());
    } finally {
      // verify
      verify(dbMock).beginTx();
      verifyTransactionFailed();
      verifyZeroInteractions(relationConverterMock, sourceNodeMock);
    }
  }

  @Test(expected = StorageException.class)
  public void addRelationThrowsAStorageExceptionWhenTheSourceCannotBeFound() throws Exception {
    // setup
    noLatestNodeFoundFor(PRIMITIVE_DOMAIN_ENTITY_TYPE, RELATION_SOURCE_ID);

    SubARelation relation = new SubARelation();
    relation.setSourceId(RELATION_SOURCE_ID);
    relation.setSourceType(PRIMITIVE_DOMAIN_ENTITY_NAME);

    try {
      // action
      instance.addRelation(RELATION_TYPE, relation, new Change());
    } finally {
      // verify
      verifyTransactionFailed();
    }
  }

  @Test(expected = StorageException.class)
  public void addRelationThrowsAStorageExceptionWhenTheTargetCannotBeFound() throws Exception {
    // setup
    latestNodeFoundFor(PRIMITIVE_DOMAIN_ENTITY_TYPE, RELATION_SOURCE_ID, aNode().build());
    noLatestNodeFoundFor(PRIMITIVE_DOMAIN_ENTITY_TYPE, RELATION_TARGET_ID);

    SubARelation relation = new SubARelation();
    relation.setSourceId(RELATION_SOURCE_ID);
    relation.setSourceType(PRIMITIVE_DOMAIN_ENTITY_NAME);
    relation.setTargetType(PRIMITIVE_DOMAIN_ENTITY_NAME);
    relation.setTargetId(RELATION_TARGET_ID);

    try {
      // action
      instance.addRelation(RELATION_TYPE, relation, new Change());
    } finally {
      // verify
      verifyTransactionFailed();
    }
  }

  @Test(expected = StorageException.class)
  public void addRelationThrowsAStorageExceptionWhenRelationTypeCannotBeFound() throws Exception {
    // setup
    latestNodeFoundFor(PRIMITIVE_DOMAIN_ENTITY_TYPE, RELATION_SOURCE_ID, aNode().build());
    latestNodeFoundFor(PRIMITIVE_DOMAIN_ENTITY_TYPE, RELATION_TARGET_ID, aNode().build());
    noLatestNodeFoundFor(RELATIONTYPE_TYPE, RELATION_TYPE_ID);

    SubARelation relation = aRelation()//
        .withSourceId(RELATION_SOURCE_ID) //
        .withSourceType(PRIMITIVE_DOMAIN_ENTITY_NAME) //
        .withTargetId(RELATION_TARGET_ID) //
        .withTargetType(PRIMITIVE_DOMAIN_ENTITY_NAME) //
        .withTypeId(RELATION_TYPE_ID) //
        .withTypeType(RELATION_TYPE_NAME).build();

    try {
      // action
      instance.addRelation(RELATION_TYPE, relation, new Change());
    } finally {
      // verify
      verifyTransactionFailed();
    }
  }

  private NodeConverter<RelationType> relationTypeWithRegularNameExists(String name) throws Exception {
    Node relationTypeNodeMock = aNode().build();
    latestNodeFoundFor(RELATIONTYPE_TYPE, RELATION_TYPE_ID, relationTypeNodeMock);

    NodeConverter<RelationType> relationTypeConverter = propertyContainerConverterFactoryHasANodeConverterTypeFor(RELATIONTYPE_TYPE);
    RelationType relationType = new RelationType();
    relationType.setRegularName(name);
    when(relationTypeConverter.convertToEntity(relationTypeNodeMock)).thenReturn(relationType);

    return relationTypeConverter;
  }

  @Test
  public void getRelationReturnsTheRelationThatBelongsToTheId() throws Exception {
    // setup
    Relationship relationshipMock = aRelationship().build();
    latestRelationshipFoundForId(ID, relationshipMock);
    SubARelation relation = aRelation().build();

    RelationshipConverter<SubARelation> relationConverterMock = propertyContainerConverterFactoryHasRelationshipConverterFor(RELATION_TYPE);
    when(relationConverterMock.convertToEntity(relationshipMock)).thenReturn(relation);

    // action
    SubARelation actualRelation = instance.getRelation(RELATION_TYPE, ID);

    // verify
    assertThat(actualRelation, is(sameInstance(relation)));

    verifyTransactionSucceeded();
  }

  private void latestRelationshipFoundForId(String string, Relationship foundRelation) {
    when(neo4JLowLevelAPIMock.getLatestRelationshipById(string)).thenReturn(foundRelation);
  }

  private void noLatestRelationshipFoundForId(String string) {
    when(neo4JLowLevelAPIMock.getLatestRelationshipById(string)).thenReturn(null);
  }

  @Test
  public void getRelationReturnsNullIfTheRelationIsNotFound() throws Exception {
    // setup
    noLatestRelationshipFoundForId(ID);

    // action
    SubARelation actualRelation = instance.getRelation(RELATION_TYPE, ID);

    // verify
    assertThat(actualRelation, is(nullValue()));

    verifyTransactionSucceeded();
  }

  @Test(expected = ConversionException.class)
  public void getRelationThrowsAConversionExceptionWhenTheRelationConverterDoes() throws Exception {
    // setup
    Relationship relationshipMock = aRelationship().build();
    latestRelationshipFoundForId(ID, relationshipMock);
    SubARelation relation = aRelation().build();

    RelationshipConverter<SubARelation> relationConverterMock = propertyContainerConverterFactoryHasRelationshipConverterFor(RELATION_TYPE);
    doThrow(ConversionException.class).when(relationConverterMock).convertToEntity(relationshipMock);

    // action
    SubARelation actualRelation = instance.getRelation(RELATION_TYPE, ID);

    // verify
    assertThat(actualRelation, is(sameInstance(relation)));

    verifyTransactionFailed();
  }

  @Test(expected = StorageException.class)
  public void getRelationThrowsStorageExceptionWhenRelationshipConverterThrowsAnInstantiationException() throws Exception {
    // setup
    Relationship relationshipMock = aRelationship().build();
    latestRelationshipFoundForId(ID, relationshipMock);

    RelationshipConverter<SubARelation> relationConverterMock = propertyContainerConverterFactoryHasRelationshipConverterFor(RELATION_TYPE);
    doThrow(InstantiationException.class).when(relationConverterMock).convertToEntity(relationshipMock);

    try {
      // action
      instance.getRelation(RELATION_TYPE, ID);
    } finally {
      // verify
      verifyTransactionFailed();
    }
  }

  @Test
  public void getRelationRevisionReturnsTheRelationForTheRequestedRevision() throws Exception {
    Relationship relationshipWithPID = aRelationship()//
        .withRevision(FIRST_REVISION)//
        .withAPID()//
        .build();

    relationshipWithRevisionFound(RELATION_TYPE, ID, FIRST_REVISION, relationshipWithPID);

    SubARelation relation = aRelation().withAPID().build();
    RelationshipConverter<SubARelation> converterMock = propertyContainerConverterFactoryHasRelationshipConverterFor(RELATION_TYPE);
    when(converterMock.convertToEntity(relationshipWithPID)).thenReturn(relation);

    // action
    SubARelation actualRelation = instance.getRelationRevision(RELATION_TYPE, ID, FIRST_REVISION);

    // verify
    assertThat(actualRelation, is(sameInstance(relation)));

    verifyTransactionSucceeded();
  }

  private void relationshipWithRevisionFound(Class<SubARelation> type, String id, int revision, Relationship foundRelationship) {
    when(neo4JLowLevelAPIMock.getRelationshipWithRevision(type, id, revision)).thenReturn(foundRelationship);
  }

  private void noRelationshipWithRevisionFound(Class<SubARelation> type, String id, int revision) {
    when(neo4JLowLevelAPIMock.getRelationshipWithRevision(type, id, revision)).thenReturn(null);
  }

  @Test
  public void getRelationRevisionReturnsNullIfTheFoundRelationshipHasNoPID() throws Exception {
    Relationship relationshipWithoutPID = aRelationship().withRevision(FIRST_REVISION).build();
    relationshipWithRevisionFound(RELATION_TYPE, ID, FIRST_REVISION, relationshipWithoutPID);

    SubARelation entityWithoutPID = aRelation().build();
    RelationshipConverter<SubARelation> relationshipConverter = propertyContainerConverterFactoryHasRelationshipConverterFor(RELATION_TYPE);
    when(relationshipConverter.convertToEntity(relationshipWithoutPID)).thenReturn(entityWithoutPID);

    // action
    SubARelation relation = instance.getRelationRevision(RELATION_TYPE, ID, FIRST_REVISION);

    // verify
    assertThat(relation, is(nullValue()));
    verifyTransactionSucceeded();
  }

  @Test
  public void getRelationRevisionReturnsNullIfTheRelationshipDoesNotExist() throws Exception {
    // setup
    noRelationshipWithRevisionFound(RELATION_TYPE, ID, FIRST_REVISION);

    // action
    SubARelation relation = instance.getRelationRevision(RELATION_TYPE, ID, FIRST_REVISION);

    // verify
    assertThat(relation, is(nullValue()));
    verifyTransactionSucceeded();
  }

  private void verifyTransactionSucceeded() {
    verify(transactionMock).success();
  }

  @Test(expected = StorageException.class)
  public void getRelationRevisionThrowsAStorageExceptionIfTheRelationCannotBeInstantiated() throws Exception {
    Relationship relationshipWithPID = aRelationship()//
        .withRevision(FIRST_REVISION)//
        .withAPID()//
        .build();
    relationshipWithRevisionFound(RELATION_TYPE, ID, FIRST_REVISION, relationshipWithPID);

    RelationshipConverter<SubARelation> converter = propertyContainerConverterFactoryHasRelationshipConverterFor(RELATION_TYPE);
    when(converter.convertToEntity(relationshipWithPID)).thenThrow(new InstantiationException());

    try {
      // action
      instance.getRelationRevision(RELATION_TYPE, ID, FIRST_REVISION);
    } finally {
      // verify
      verifyTransactionFailed();
    }
  }

  private void verifyTransactionFailed() {
    verify(transactionMock).failure();
  }

  @Test(expected = ConversionException.class)
  public void getRelationRevisionThrowsAStorageExceptionIfTheRelationCannotBeConverted() throws Exception {
    Relationship relationshipWithPID = aRelationship()//
        .withRevision(FIRST_REVISION)//
        .withAPID()//
        .build();
    relationshipWithRevisionFound(RELATION_TYPE, ID, FIRST_REVISION, relationshipWithPID);

    RelationshipConverter<SubARelation> converter = propertyContainerConverterFactoryHasRelationshipConverterFor(RELATION_TYPE);
    when(converter.convertToEntity(relationshipWithPID)).thenThrow(new ConversionException());

    try {
      // action
      instance.getRelationRevision(RELATION_TYPE, ID, FIRST_REVISION);
    } finally {
      // verify
      verify(converter).convertToEntity(relationshipWithPID);
      verifyTransactionFailed();
    }
  }

  @Test
  public void getRelationsByEntityIdReturnsAStorageIteratorOfRelationForTheFoundRelationships() throws Exception {
    // setup
    ArrayList<Relationship> relationships = Lists.newArrayList();
    when(neo4JLowLevelAPIMock.getRelationshipsByNodeId(ID)).thenReturn(relationships);

    @SuppressWarnings("unchecked")
    StorageIterator<SubARelation> storageIterator = mock(StorageIterator.class);
    when(neo4jStorageIteratorFactoryMock.forRelationship(RELATION_TYPE, relationships)).thenReturn(storageIterator);

    // action
    StorageIterator<SubARelation> actualStorageIterator = instance.getRelationsByEntityId(RELATION_TYPE, ID);

    // verify
    assertThat(actualStorageIterator, is(sameInstance(storageIterator)));

    verifyTransactionSucceeded();
  }

  @Test(expected = StorageException.class)
  public void getRelationsByEntityIdThrowsAStorageExceptionWhenTheStorageIteratorCannotBeCreated() throws Exception {
    // setup
    ArrayList<Relationship> relationships = Lists.newArrayList();
    when(neo4JLowLevelAPIMock.getRelationshipsByNodeId(ID)).thenReturn(relationships);

    when(neo4jStorageIteratorFactoryMock.forRelationship(RELATION_TYPE, relationships)).thenThrow(new StorageException());

    try {
      // action
      instance.getRelationsByEntityId(RELATION_TYPE, ID);
    } finally {
      // verify
      verifyTransactionFailed();
    }
  }

  @Test
  public void updateRelationRetrievesTheRelationAndUpdateItsValuesAndAdministrativeValues() throws Exception {
    // setup
    Relationship relationship = aRelationship().withRevision(FIRST_REVISION).build();
    latestRelationshipFoundForId(ID, relationship);

    SubARelation relation = aRelation()//
        .withId(ID) //
        .withRevision(SECOND_REVISION) //
        .build();

    RelationshipConverter<SubARelation> converterMock = propertyContainerConverterFactoryHasRelationshipConverterFor(RELATION_TYPE);

    // action
    instance.updateRelation(RELATION_TYPE, relation, CHANGE);

    // verify
    verify(converterMock).updatePropertyContainer( //
        relationship, //
        relation);
    verify(converterMock).updateModifiedAndRev( //
        relationship, //
        relation);
    verifyTransactionSucceeded();
  }

  @Test(expected = UpdateException.class)
  public void updateRelationThrowsAnUpdateExceptionWhenTheRelationshipToUpdateCannotBeFound() throws Exception {
    // setup
    noLatestRelationshipFoundForId(ID);

    SubARelation relation = aRelation()//
        .withId(ID)//
        .withRevision(FIRST_REVISION)//
        .build();
    try {
      // action
      instance.updateRelation(RELATION_TYPE, relation, CHANGE);
    } finally {
      // verify
      verifyTransactionFailed();
    }
  }

  @Test(expected = UpdateException.class)
  public void updateRelationThrowsAnUpdateExceptionWhenRevOfTheRelationshipIsHigherThanThatOfTheEntity() throws Exception {
    testUpdateRelationRevisionUpdateException(SECOND_REVISION, FIRST_REVISION);
  }

  @Test(expected = UpdateException.class)
  public void updateRelationThrowsAnUpdateExceptionWhenRevOfTheRelationshipMoreThanOneIsLowerThanThatOfTheEntity() throws Exception {
    testUpdateRelationRevisionUpdateException(FIRST_REVISION, THIRD_REVISION);
  }

  @Test(expected = UpdateException.class)
  public void updateRelationThrowsAnUpdateExceptionWhenRevOfTheRelationshipIsEqualToThatOfTheEntity() throws Exception {
    testUpdateRelationRevisionUpdateException(FIRST_REVISION, FIRST_REVISION);
  }

  private void testUpdateRelationRevisionUpdateException(int relationshipRev, int relationRev) throws StorageException {
    // setup
    Relationship relationshipWithHigherRev = aRelationship().withRevision(relationshipRev).build();
    latestRelationshipFoundForId(ID, relationshipWithHigherRev);

    SubARelation relation = aRelation()//
        .withId(ID)//
        .withRevision(relationRev) //
        .build();

    try {
      // action
      instance.updateRelation(RELATION_TYPE, relation, CHANGE);
    } finally {
      // verify
      verify(dbMock).beginTx();

      verifyTransactionFailed();
    }
  }

  @Test(expected = ConversionException.class)
  public void updateRelationThrowsAConversionExceptionWhenTheRelationshipConverterThrowsOne() throws Exception {
    // setup
    Relationship relationship = aRelationship().withRevision(FIRST_REVISION).build();
    latestRelationshipFoundForId(ID, relationship);

    Change oldModified = CHANGE;
    SubARelation relation = aRelation()//
        .withId(ID) //
        .withRevision(SECOND_REVISION) //
        .build();

    RelationshipConverter<SubARelation> converterMock = propertyContainerConverterFactoryHasRelationshipConverterFor(RELATION_TYPE);
    doThrow(ConversionException.class).when(converterMock).updatePropertyContainer(relationship, relation);

    try {
      // action
      instance.updateRelation(RELATION_TYPE, relation, oldModified);
    } finally {
      // verify
      verify(dbMock).beginTx();
      verify(converterMock).updatePropertyContainer(relationship, relation);
      verifyTransactionFailed();
    }
  }

  @Test
  public void setRelationPIDSetsThePIDOfTheRelationAndDuplicatesIt() throws Exception {
    // setup
    Relationship relationship = aRelationship().withRevision(SECOND_REVISION).build();
    latestRelationshipFoundForId(ID, relationship);

    SubARelation entity = aRelation().build();

    RelationshipConverter<SubARelation> converter = propertyContainerConverterFactoryHasRelationshipConverterFor(RELATION_TYPE);
    when(converter.convertToEntity(relationship)).thenReturn(entity);

    try {
      // action
      instance.setRelationPID(RELATION_TYPE, ID, PID);
    } finally {
      // verify
      verify(converter).addValuesToPropertyContainer(//
          argThat(equalTo(relationship)), //
          argThat(likeDomainEntity(RELATION_TYPE)//
              .withPID(PID)));
      verify(relationshipDuplicatorMock).saveDuplicate(relationship);
      verifyTransactionSucceeded();
    }
  }

  @Test(expected = IllegalStateException.class)
  public void setRelationPIDThrowsAnIllegalStateExceptionIfTheRelationAlreadyHasAPID() throws Exception {
    // setup
    Relationship relationshipWithPID = aRelationship().withAPID().build();
    latestRelationshipFoundForId(ID, relationshipWithPID);

    SubARelation entityWithAPID = aRelation().withAPID().build();

    RelationshipConverter<SubARelation> converter = propertyContainerConverterFactoryHasRelationshipConverterFor(RELATION_TYPE);
    when(converter.convertToEntity(relationshipWithPID)).thenReturn(entityWithAPID);

    try {
      // action
      instance.setRelationPID(RELATION_TYPE, ID, PID);
    } finally {
      // verify
      verifyTransactionFailed();
    }
  }

  @Test(expected = ConversionException.class)
  public void setRelationPIDThrowsAConversionExceptionIfTheRelationshipCannotBeConverted() throws Exception {
    // setup
    Relationship relationship = aRelationship().build();
    latestRelationshipFoundForId(ID, relationship);

    RelationshipConverter<SubARelation> converter = propertyContainerConverterFactoryHasRelationshipConverterFor(RELATION_TYPE);
    when(converter.convertToEntity(relationship)).thenThrow(new ConversionException());

    try {
      // action
      instance.setRelationPID(RELATION_TYPE, ID, PID);
    } finally {
      // verify
      verifyTransactionFailed();
    }
  }

  @Test(expected = ConversionException.class)
  public void setRelationPIDThrowsAConversionsExceptionWhenTheUpdatedEntityCannotBeConvertedToARelationship() throws Exception {
    // setup
    Relationship relationship = aRelationship().withRevision(SECOND_REVISION).build();
    latestRelationshipFoundForId(ID, relationship);

    SubARelation entity = aRelation().build();

    RelationshipConverter<SubARelation> converter = propertyContainerConverterFactoryHasRelationshipConverterFor(RELATION_TYPE);
    when(converter.convertToEntity(relationship)).thenReturn(entity);
    doThrow(ConversionException.class).when(converter).addValuesToPropertyContainer(relationship, entity);

    try {
      // action
      instance.setRelationPID(RELATION_TYPE, ID, PID);
    } finally {
      // verify
      verifyTransactionFailed();
    }
  }

  @Test(expected = StorageException.class)
  public void setRelationPIDThrowsAStorageExceptionIfTheRelationCannotBeInstatiated() throws Exception {
    // setup
    Relationship relationship = aRelationship().build();
    latestRelationshipFoundForId(ID, relationship);

    RelationshipConverter<SubARelation> converter = propertyContainerConverterFactoryHasRelationshipConverterFor(RELATION_TYPE);
    when(converter.convertToEntity(relationship)).thenThrow(new InstantiationException());

    try {
      // action
      instance.setRelationPID(RELATION_TYPE, ID, PID);
    } finally {
      // verify
      verifyTransactionFailed();
    }
  }

  @Test(expected = NoSuchEntityException.class)
  public void setRelationPIDThrowsANoSuchEntityExceptionIfTheRelationshipCannotBeFound() throws Exception {
    // setup
    noLatestRelationshipFoundForId(ID);

    try {
      // action
      instance.setRelationPID(RELATION_TYPE, ID, PID);
    } finally {
      verifyTransactionFailed();
    }

  }

  private <T extends Relation> RelationshipConverter<T> propertyContainerFactoryHasCompositeRelationshipConverterFor(Class<T> type) {
    @SuppressWarnings("unchecked")
    RelationshipConverter<T> relationshipConverter = mock(RelationshipConverter.class);
    when(propertyContainerConverterFactoryMock.createCompositeForRelation(type)).thenReturn(relationshipConverter);

    return relationshipConverter;
  }

  private <T extends Relation> RelationshipConverter<T> propertyContainerConverterFactoryHasRelationshipConverterFor(Class<T> type) {
    @SuppressWarnings("unchecked")
    RelationshipConverter<T> relationshipConverter = mock(RelationshipConverter.class);
    when(propertyContainerConverterFactoryMock.createForRelation(type)).thenReturn(relationshipConverter);

    return relationshipConverter;
  }

  @Test
  public void findRelationByPropertySearchesTheRelationshipByPropertyAndConvertsIt() throws Exception {
    // setup
    Relationship relationship = aRelationship().build();
    relationshipFoundFor(PROPERTY_NAME, PROPERTY_VALUE, relationship);

    RelationshipConverter<SubARelation> relationshipConverter = propertyContainerConverterFactoryHasRelationshipConverterFor(RELATION_TYPE);
    when(relationshipConverter.getPropertyName(RELATION_FIELD_NAME)).thenReturn(PROPERTY_NAME);
    SubARelation relation = aRelation().build();
    when(relationshipConverter.convertToEntity(relationship)).thenReturn(relation);

    // action
    SubARelation actualRelation = instance.findRelationByProperty(RELATION_TYPE, RELATION_FIELD_NAME, PROPERTY_VALUE);

    // verify
    assertThat(actualRelation, is(sameInstance(relation)));
    verifyTransactionSucceeded();
  }

  @Test
  public void findRelationByPropertyReturnsNullIfTheRelationshipCannotBeFound() throws Exception {
    // setup
    noRelationshipFoundFor(PROPERTY_NAME, PROPERTY_VALUE);
    RelationshipConverter<SubARelation> relationshipConverter = propertyContainerConverterFactoryHasRelationshipConverterFor(RELATION_TYPE);
    when(relationshipConverter.getPropertyName(RELATION_FIELD_NAME)).thenReturn(PROPERTY_NAME);

    // action
    SubARelation actualRelation = instance.findRelationByProperty(RELATION_TYPE, RELATION_FIELD_NAME, PROPERTY_VALUE);

    // verify
    assertThat(actualRelation, is(nullValue()));
    verifyTransactionSucceeded();
  }

  private void noRelationshipFoundFor(String propertyName, String propertyValue) {
    when(neo4JLowLevelAPIMock.findRelationshipByProperty(RELATION_TYPE, propertyName, propertyValue)).thenReturn(null);
  }

  @Test(expected = ConversionException.class)
  public void findRelationByPropertyThrowsAConversionExceptionIfTheRelationshipCannotBeConverted() throws Exception {
    // setup
    Relationship relationship = aRelationship().build();
    relationshipFoundFor(PROPERTY_NAME, PROPERTY_VALUE, relationship);

    RelationshipConverter<SubARelation> relationshipConverter = propertyContainerConverterFactoryHasRelationshipConverterFor(RELATION_TYPE);
    when(relationshipConverter.getPropertyName(RELATION_FIELD_NAME)).thenReturn(PROPERTY_NAME);
    when(relationshipConverter.convertToEntity(relationship)).thenThrow(new ConversionException());

    try {
      // action
      instance.findRelationByProperty(RELATION_TYPE, RELATION_FIELD_NAME, PROPERTY_VALUE);
    } finally {
      // verify
      verifyTransactionFailed();
    }
  }

  private void relationshipFoundFor(String propertyName, String propertyValue, Relationship relationship) {
    when(neo4JLowLevelAPIMock.findRelationshipByProperty(RELATION_TYPE, propertyName, propertyValue)).thenReturn(relationship);
  }

  @Test(expected = StorageException.class)
  public void findRelationByPropertyThrowsAStorageExceptionIfTheRelationCannotBeInstantiated() throws Exception {
    // setup
    Relationship relationship = aRelationship().build();
    relationshipFoundFor(PROPERTY_NAME, PROPERTY_VALUE, relationship);

    RelationshipConverter<SubARelation> relationshipConverter = propertyContainerConverterFactoryHasRelationshipConverterFor(RELATION_TYPE);
    when(relationshipConverter.getPropertyName(RELATION_FIELD_NAME)).thenReturn(PROPERTY_NAME);
    when(relationshipConverter.convertToEntity(relationship)).thenThrow(new InstantiationException());

    try {
      // action
      instance.findRelationByProperty(RELATION_TYPE, RELATION_FIELD_NAME, PROPERTY_VALUE);
    } finally {
      // verify
      verifyTransactionFailed();
    }
  }

  @Test
  public void countRelationDelegatesToNeo4JLowLevelAPI() {
    // setup
    long count = 5l;
    when(neo4JLowLevelAPIMock.countRelationships()).thenReturn(count);

    // action
    long actualCount = instance.countRelations(RELATION_TYPE);

    // verify
    assertThat(actualCount, is(equalTo(count)));
  }

  @Test
  public void relationExistsTriesToRetrieveTheRelationshipWithTheIdAndReturnsTrueIfTheRelationshipExists() {
    // setup
    Relationship aRelationship = aRelationship().build();
    when(neo4JLowLevelAPIMock.getLatestRelationshipById(ID)).thenReturn(aRelationship);

    // action
    boolean exists = instance.relationExists(RELATION_TYPE, ID);

    // verify
    assertThat(exists, is(true));
    verifyTransactionSucceeded();
  }

  @Test
  public void relationExistsTriesToRetrieveTheRelationshipWithTheIdAndReturnsFalseIfTheRelationshipExists() {
    // setup
    when(neo4JLowLevelAPIMock.getLatestRelationshipById(ID)).thenReturn(null);

    // action
    boolean exists = instance.relationExists(RELATION_TYPE, ID);

    // verify
    assertThat(exists, is(false));
    verifyTransactionSucceeded();
  }

  @Test
  public void findRelationRetrievesTheLatestRelationWithTheSourceTargetAndType() throws Exception {
    // setup
    Relationship relationship = aRelationship().build();
    when(neo4JLowLevelAPIMock.findLatestRelationshipFor(RELATION_TYPE, RELATION_SOURCE_ID, RELATION_TARGET_ID, RELATION_TYPE_ID)).thenReturn(relationship);

    RelationshipConverter<SubARelation> converter = propertyContainerConverterFactoryHasRelationshipConverterFor(RELATION_TYPE);
    SubARelation relation = aRelation().build();
    when(converter.convertToEntity(relationship)).thenReturn(relation);

    // action
    SubARelation actualRelation = instance.findRelation(RELATION_TYPE, RELATION_SOURCE_ID, RELATION_TARGET_ID, RELATION_TYPE_ID);

    // verify
    assertThat(actualRelation, is(sameInstance(relation)));

    verifyTransactionSucceeded();
  }

  @Test
  public void findRelationReturnsNullWhenTheRelationIsNotFound() throws Exception {
    // setup
    when(neo4JLowLevelAPIMock.findLatestRelationshipFor(RELATION_TYPE, RELATION_SOURCE_ID, RELATION_TARGET_ID, RELATION_TYPE_ID)).thenReturn(null);

    // action
    SubARelation relation = instance.findRelation(RELATION_TYPE, RELATION_SOURCE_ID, RELATION_TARGET_ID, RELATION_TYPE_ID);

    // verify
    assertThat(relation, is(nullValue()));
    verifyTransactionSucceeded();

  }

  @Test(expected = ConversionException.class)
  public void findRelationThrowsAConversionExceptionWhenTheRelationshipCannotBeConverted() throws Exception {
    findRelationRelationCannotBeConverted(new ConversionException());
  }

  private void findRelationRelationCannotBeConverted(Exception exceptionToThrow) throws Exception {
    // setup
    Relationship relationship = aRelationship().build();
    when(neo4JLowLevelAPIMock.findLatestRelationshipFor(RELATION_TYPE, RELATION_SOURCE_ID, RELATION_TARGET_ID, RELATION_TYPE_ID)).thenReturn(relationship);

    RelationshipConverter<SubARelation> converter = propertyContainerConverterFactoryHasRelationshipConverterFor(RELATION_TYPE);
    when(converter.convertToEntity(relationship)).thenThrow(exceptionToThrow);

    try {
      // action
      instance.findRelation(RELATION_TYPE, RELATION_SOURCE_ID, RELATION_TARGET_ID, RELATION_TYPE_ID);
    } finally {
      // verify
      verifyTransactionFailed();
    }
  }

  @Test(expected = StorageException.class)
  public void findRelationThrowsAStorageExceptionWhenTheRelationCannotBeInstantiated() throws Exception {
    findRelationRelationCannotBeConverted(new InstantiationException());
  }

  // TODO find a better implementation see TIM-143
  @Test
  public void getIdsOfNonPersistentRelationsReturnsTheIdsOfAllNoPersistenRelations() {
    // setup
    String nonPersistentId1 = "id1";
    Relationship nonPersistentRelationship1 = aRelationship().withId(nonPersistentId1).build();
    String persistentId1 = "id2";
    Relationship persistentRelationship1 = aRelationship().withId(persistentId1).withAPID().build();
    Node node1 = aNode()//
        .withOutgoingRelationShip(nonPersistentRelationship1)//
        .andOutgoingRelationship(persistentRelationship1)//
        .build();

    String nonPersistentId2 = "id3";
    Relationship nonPersistentRelationship2 = aRelationship().withId(nonPersistentId2).build();
    String persistentId2 = "id4";
    Relationship persistentRelationship2 = aRelationship().withId(persistentId2).withAPID().build();
    Node node2 = aNode()//
        .withOutgoingRelationShip(persistentRelationship2)//
        .andOutgoingRelationship(nonPersistentRelationship2)//
        .build();

    ResourceIterator<Node> foundNodes = aNodeSearchResult()//
        .withPropertyContainer(node1)//
        .andPropertyContainer(node2).asIterator();

    when(neo4JLowLevelAPIMock.getAllNodes()).thenReturn(foundNodes);

    // action
    List<String> idsOfNonPersistentRelations = instance.getIdsOfNonPersistentRelations(RELATION_TYPE);

    // verify
    assertThat(idsOfNonPersistentRelations, containsInAnyOrder(nonPersistentId1, nonPersistentId2));
    assertThat(idsOfNonPersistentRelations, not(hasItem(persistentId1)));
    assertThat(idsOfNonPersistentRelations, not(hasItem(persistentId2)));

    verify(transactionMock).success();
  }

  /* *****************************************************************************
   * Other methods
   * *****************************************************************************/

  @Test
  public void closeCallsTheDBToClose() {
    // action
    instance.close();

    // verify
    verify(dbMock).shutdown();
  }

  @Test
  public void isAvailableClassTheDBWithATimeout() {
    // setup
    boolean available = true;
    when(dbMock.isAvailable(REQUEST_TIMEOUT)).thenReturn(available);

    // action
    boolean actualAvailable = instance.isAvailable();

    // verify
    assertThat(actualAvailable, is(equalTo(available)));

    verify(dbMock).isAvailable(REQUEST_TIMEOUT);
  }
}