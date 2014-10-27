package nl.knaw.huygens.timbuctoo.tools.importer.neww;

/*
 * #%L
 * Timbuctoo tools
 * =======
 * Copyright (C) 2012 - 2014 Huygens ING
 * =======
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 3 of the 
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public 
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/gpl-3.0.html>.
 * #L%
 */

import java.io.PrintWriter;
import java.util.List;
import java.util.Map;
import java.util.Set;

import nl.knaw.huygens.tei.DelegatingVisitor;
import nl.knaw.huygens.tei.Element;
import nl.knaw.huygens.tei.ElementHandler;
import nl.knaw.huygens.tei.Traversal;
import nl.knaw.huygens.tei.XmlContext;
import nl.knaw.huygens.tei.handlers.DefaultElementHandler;
import nl.knaw.huygens.timbuctoo.config.TypeRegistry;
import nl.knaw.huygens.timbuctoo.model.Collective;
import nl.knaw.huygens.timbuctoo.model.Document;
import nl.knaw.huygens.timbuctoo.model.DomainEntity;
import nl.knaw.huygens.timbuctoo.model.Person;
import nl.knaw.huygens.timbuctoo.model.Reference;
import nl.knaw.huygens.timbuctoo.model.RelationType;
import nl.knaw.huygens.timbuctoo.model.cwch.CWCHCollective;
import nl.knaw.huygens.timbuctoo.model.cwch.CWCHDocument;
import nl.knaw.huygens.timbuctoo.model.cwch.CWCHPerson;
import nl.knaw.huygens.timbuctoo.model.cwno.CWNORelation;
import nl.knaw.huygens.timbuctoo.model.util.Datable;
import nl.knaw.huygens.timbuctoo.model.util.Link;
import nl.knaw.huygens.timbuctoo.model.util.Period;
import nl.knaw.huygens.timbuctoo.model.util.PersonName;
import nl.knaw.huygens.timbuctoo.model.util.PersonNameComponent;
import nl.knaw.huygens.timbuctoo.tools.importer.CaptureHandler;
import nl.knaw.huygens.timbuctoo.tools.importer.RelationDTO;
import nl.knaw.huygens.timbuctoo.tools.importer.RelationTypeImporter;
import nl.knaw.huygens.timbuctoo.tools.process.Pipeline;
import nl.knaw.huygens.timbuctoo.tools.process.Progress;

import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

/**
 * Reads data from Swiss COBWWWEB webservice and converts it to json.
 *
 * The dataset contains persons, documents and relations between those.
 * Relations are definied in terms of id's supplied by the service,
 * they have no meaning outside that context.
 */
public class CobwwwebChConverter extends CobwwwebConverter {

  private static final String VRE_ID = "cwch";
  private static final String URL = "http://www.arcadia.uzh.ch/repository";

  public static void main(String[] args) throws Exception {
    Pipeline.execute(new CobwwwebChConverter());
  }

  // -------------------------------------------------------------------

  /** References to primitive entities */
  private final Map<String, Reference> references = Maps.newHashMap();
  private Set<String> relationTypeNames;

  public CobwwwebChConverter() {
    super(VRE_ID);
  }

  @Override
  public String getDescription() {
    return "Convert Swiss COBWWWEB data";
  }

  @Override
  public void call() throws Exception {
    try {
      openLog(getClass().getSimpleName() + ".txt");

      RelationTypeImporter importer = new RelationTypeImporter();
      importer.call(RelationTypeImporter.RELATION_TYPE_DEFS);
      relationTypeNames = importer.getNames();

      printBoxedText("Collectives");
      // convertCollectives();

      printBoxedText("Persons");
      convertPersons();

      printBoxedText("Documents");
      // convertDocuments();

      printBoxedText("Relations");
      // convertRelations();
    } finally {
      displayErrorSummary();
      closeLog();
    }
  }

  private void storeReference(String key, Class<? extends DomainEntity> type, String id) {
    Reference reference = new Reference(TypeRegistry.toBaseDomainEntity(type), id);
    if (references.put(key, reference) != null) {
      log("Duplicate key '%s'%n", key);
      System.exit(-1);
    }
  }

  // --- collectives -----------------------------------------------------------

  private void convertCollectives() throws Exception {
    Progress progress = new Progress();
    PrintWriter out = createPrintWriter(CWCHCollective.class);

    try {
      String xml = getResource(URL, "cooperations");
      List<String> ids = parseIdResource(xml, "CooperationId");
      System.out.println(ids.size());
      for (String id : ids) {
        progress.step();
        xml = getResource(URL, "cooperation", id);
        CWCHCollective entity = parseCollectiveResource(xml, id);
        jsonConverter.appendTo(out, entity);
        storeReference(id, CWCHCollective.class, id);
      }
    } finally {
      out.close();
      progress.done();
    }
  }

  private CWCHCollective parseCollectiveResource(String xml, String id) {
    CollectiveContext context = new CollectiveContext(xml, id);
    parseXml(xml, new CollectiveVisitor(context));
    return context.entity;
  }

  private class CollectiveContext extends XmlContext {
    private String xml;
    private String id;
    public CWCHCollective entity = new CWCHCollective();

    public CollectiveContext(String xml, String id) {
      this.xml = xml;
      this.id = id;
    }

    public void error(String format, Object... args) {
      log("[%s] %s%n", id, String.format(format, args));
    }
  }

  /*
   * <cooperation>
   *   <CooperationId>colonia-0</CooperationId>
   *   <Type>Academy</Type>
   *   <Names>Arcadia</Names>
   *   <StartDate>1690</StartDate>
   *   <Location>Roma</Location>
   *   <Reference/>
   *   <lastedited>2014-08-04T10:50:14Z</lastedited>
   * </cooperation>
   */

  private class CollectiveVisitor extends DelegatingVisitor<CollectiveContext> {
    public CollectiveVisitor(CollectiveContext context) {
      super(context);
      setDefaultElementHandler(new DefaultCollectiveHandler());
      addElementHandler(new CollectiveIdHandler(), "CooperationId");
      addElementHandler(new CollectiveLinkHandler(), "Reference");
      addElementHandler(new CollectiveLocationHandler(), "Location");
      addElementHandler(new CollectiveNamesHandler(), "Names");
      addElementHandler(new CollectiveStartDateHandler(), "StartDate");
      addElementHandler(new CollectiveTypeHandler(), "Type");
    }
  }

  private class DefaultCollectiveHandler extends DefaultElementHandler<CollectiveContext> {
    private final Set<String> ignoredNames = Sets.newHashSet("arcadia", "cooperation", "lastedited", "request", "responseDate");

    @Override
    public Traversal enterElement(Element element, CollectiveContext context) {
      String name = element.getName();
      if (!ignoredNames.contains(name)) {
        context.error("Unexpected element: %s%nxml: %s", name, context.xml);
      }
      return Traversal.NEXT;
    }
  }

  private class CollectiveIdHandler extends CaptureHandler<CollectiveContext> {
    @Override
    public void handleContent(Element element, CollectiveContext context, String text) {
      if (!context.id.equals(text)) {
        context.error("ID mismatch: %s", text);
      }
    }
  }

  private class CollectiveLinkHandler extends CaptureHandler<CollectiveContext> {
    @Override
    public void handleContent(Element element, CollectiveContext context, String text) {
      context.entity.addLink(new Link(text));
    }
  }

  private class CollectiveLocationHandler extends CaptureHandler<CollectiveContext> {
    @Override
    public void handleContent(Element element, CollectiveContext context, String text) {
      context.entity.tempLocation = text;
    }
  }

  private class CollectiveNamesHandler extends CaptureHandler<CollectiveContext> {
    @Override
    public void handleContent(Element element, CollectiveContext context, String text) {
       context.entity.setName(text);
    }
  }

  private class CollectiveStartDateHandler extends CaptureHandler<CollectiveContext> {
    @Override
    public void handleContent(Element element, CollectiveContext context, String text) {
       Period period = new Period(text, null);
       context.entity.setPeriod(period);
    }
  }

  private class CollectiveTypeHandler extends CaptureHandler<CollectiveContext> {
    @Override
    public void handleContent(Element element, CollectiveContext context, String text) {
      String normalized = Collective.Type.normalize(text);
      if (normalized.equals(Collective.Type.UNKNOWN)) {
        context.error("Unknown type: %s", text);
      }
      context.entity.setType(normalized);
    }
  }

  // --- persons ---------------------------------------------------------------
  
  private void convertPersons() throws Exception {
    Progress progress = new Progress();
    PrintWriter out = createPrintWriter(CWCHPerson.class);

    try {
      String xml = getResource(URL, "persons");
      List<String> personIds = parseIdResource(xml, "PersonId");

      for (String id : personIds) {
        progress.step();
        xml = getResource(URL, "person", id);
        CWCHPerson entity = parsePersonResource(xml, id);
        jsonConverter.appendTo(out, entity);
        storeReference(id, CWCHPerson.class, id);
      }
    } finally {
      out.close();
      progress.done();
    }
  }

  private CWCHPerson parsePersonResource(String xml, String id) {
    PersonContext context = new PersonContext(xml, id);
    parseXml(xml, new PersonVisitor(context));
    return context.entity;
  }

  private class PersonContext extends XmlContext {
    private String xml;
    private String id;
    public PersonName personName;
    public CWCHPerson entity = new CWCHPerson();

    public PersonContext(String xml, String id) {
      this.xml = xml;
      this.id = id;
    }

    public void error(String format, Object... args) {
        log("[%s] %s%n", id, String.format(format, args));
      }
  }

  /*
   * <person>
   *   <PersonId>donne-1</PersonId>
   *   <Names>
   *     <surname>Accarigi</surname>
   *     <forename>Livia</forename>
   *     <addname>Delinda Calcidica</addname>
   *   </Names>
   *   <gender>2</gender>
   *   <Reference/>
   *   <lastedited>2014-08-04T10:50:46Z</lastedited>
   * </person>
   */

  private class PersonVisitor extends DelegatingVisitor<PersonContext> {
    public PersonVisitor(PersonContext context) {
      super(context);
      setDefaultElementHandler(new DefaultPersonHandler());
      addElementHandler(new PersonIdHandler(), "PersonId");
      addElementHandler(new PersonLinkHandler(), "Reference");
      addElementHandler(new GenderHandler(), "gender");
      addElementHandler(new NameHandler(), "Names");
      addElementHandler(new NameComponentHandler(), "addname", "forename", "surname");
    }
  }

  private class DefaultPersonHandler extends DefaultElementHandler<PersonContext> {
    private final Set<String> ignoredNames = Sets.newHashSet("arcadia", "lastedited", "Names", "person", "request", "responseDate");

    @Override
    public Traversal enterElement(Element element, PersonContext context) {
      String name = element.getName();
      if (!ignoredNames.contains(name)) {
        context.error("Unexpected element: %s%nxml: %s", name, context.xml);
      }
      return Traversal.NEXT;
    }
  }

  private class PersonIdHandler extends CaptureHandler<PersonContext> {
    @Override
    public void handleContent(Element element, PersonContext context, String text) {
      context.entity.setId(text);
      if (!context.id.equals(text)) {
        context.error("ID mismatch: %s", text);
      }
    }
  }

  private class PersonLinkHandler extends CaptureHandler<PersonContext> {
    @Override
    public void handleContent(Element element, PersonContext context, String text) {
      context.entity.addLink(new Link(text));
    }
  }

  private class GenderHandler extends CaptureHandler<PersonContext> {
    @Override
    public void handleContent(Element element, PersonContext context, String text) {
      if (text.equals("1")) {
        context.entity.setGender(Person.Gender.MALE);
      } else if (text.equals("2")) {
        context.entity.setGender(Person.Gender.FEMALE);
      } else if (text.equals("9")) {
        context.entity.setGender(Person.Gender.NOT_APPLICABLE);
      } else {
        context.entity.setGender(Person.Gender.UNKNOWN);
      }
    }
  }

  private class NameHandler implements ElementHandler<PersonContext> {
    @Override
    public Traversal enterElement(Element element, PersonContext context) {
      context.personName = new PersonName();
      return Traversal.NEXT;
    }

    @Override
    public Traversal leaveElement(Element element, PersonContext context) {
      if (context.personName.getComponents().size() != 0) {
        context.entity.addName(context.personName);
      }
      return Traversal.NEXT;
    }
  }

  private class NameComponentHandler implements ElementHandler<PersonContext> {
    @Override
    public Traversal enterElement(Element element, PersonContext context) {
      context.openLayer();
      return Traversal.NEXT;
    }

    @Override
    public Traversal leaveElement(Element element, PersonContext context) {
      String text = context.closeLayer();
      if (element.hasName("forename")) {
        context.personName.addNameComponent(PersonNameComponent.Type.FORENAME, text);
      } else if (element.hasName("surname")) {
          context.personName.addNameComponent(PersonNameComponent.Type.SURNAME, text);
      } else if (element.hasName("addname")) {
          context.personName.addNameComponent(PersonNameComponent.Type.ADD_NAME, text);
      } else {
        context.error("Unknown component: %s", element.getName());
      }
      return Traversal.NEXT;
    }
  }

  // --- documents -------------------------------------------------------------

  private void convertDocuments() throws Exception {
    Progress progress = new Progress();
    PrintWriter out = createPrintWriter(CWCHDocument.class);

    try {
      String xml = getResource(URL, "documents");
      System.out.println(xml);
      List<String> documentIds = parseIdResource(xml, "DocumentId");

      for (String id : documentIds) {
        progress.step();
        xml = getResource(URL, "document", id);
        System.out.println(xml);
        CWCHDocument entity = parseDocumentResource(xml, id);
        jsonConverter.appendTo(out, entity);
        storeReference(id, CWCHDocument.class, id);
      }
    } finally {
      out.close();
      progress.done();
    }
  }

  private CWCHDocument parseDocumentResource(String xml, String id) {
    DocumentContext context = new DocumentContext(id);
    parseXml(xml, new DocumentVisitor(context));
    return context.document;
  }

  private class DocumentContext extends XmlContext {
    public String id;
    public CWCHDocument document = new CWCHDocument();

    public DocumentContext(String id) {
      this.id = id;
    }

    public void error(String format, Object... args) {
      System.err.printf("## [%s] %s%n", id, String.format(format, args));
    }
  }

  private class DocumentVisitor extends DelegatingVisitor<DocumentContext> {
    public DocumentVisitor(DocumentContext context) {
      super(context);
      setDefaultElementHandler(new DefaultDocumentHandler());
      addElementHandler(new DocumentIdHandler(), "DocumentId");
      addElementHandler(new DocumentTypeHandler(), "type");
      addElementHandler(new DocumentTitleHandler(), "Title");
      addElementHandler(new DocumentDateHandler(), "date");
    }
  }

  private class DefaultDocumentHandler extends DefaultElementHandler<DocumentContext> {
    private final Set<String> ignoredNames = Sets.newHashSet("arcadia", "document", "responseDate", "request", "lastedited");

    @Override
    public Traversal enterElement(Element element, DocumentContext context) {
      String name = element.getName();
      if (!ignoredNames.contains(name)) {
        context.error("Unexpected element: %s", name);
      }
      return Traversal.NEXT;
    }
  }

  private class DocumentIdHandler extends CaptureHandler<DocumentContext> {
    @Override
    public void handleContent(Element element, DocumentContext context, String text) {
      context.document.setId(text);
      if (!context.id.equals(text)) {
        context.error("ID mismatch: %s", text);
      }
    }
  }

  private class DocumentTypeHandler extends CaptureHandler<DocumentContext> {
    @Override
    public void handleContent(Element element, DocumentContext context, String text) {
      if (text.equalsIgnoreCase(Document.DocumentType.WORK.name())) {
        context.document.setDocumentType(Document.DocumentType.WORK);
      } else {
        context.error("Unknown type: %s", text);
      }
    }
  }

  private class DocumentTitleHandler extends CaptureHandler<DocumentContext> {
    @Override
    public void handleContent(Element element, DocumentContext context, String text) {
      context.document.setTitle(text);
    }
  }

  private class DocumentDescriptionHandler extends CaptureHandler<DocumentContext> {
    @Override
    public void handleContent(Element element, DocumentContext context, String text) {
      context.document.setDescription(text);
    }
  }

  private class DocumentDateHandler extends CaptureHandler<DocumentContext> {
    @Override
    public void handleContent(Element element, DocumentContext context, String text) {
      Datable datable = new Datable(text);
      context.document.setDate(datable);
    }
  }

  // --- relations -------------------------------------------------------------

  private void convertRelations() throws Exception {
    Progress progress = new Progress();
    PrintWriter out = createPrintWriter(CWNORelation.class);

    try {
      String xml = getResource(URL, "relations");
      List<String> relationIds = parseIdResource(xml, "RelationId");
      System.out.println(relationIds.size());

      for (String id : relationIds) {
        progress.step();
        System.out.println(xml);
        RelationDTO relation = parseRelationResource(xml, id);

        jsonConverter.appendTo(out, relation);
      }
    } finally {
      out.close();
      progress.done();
    }
  }

  private RelationDTO parseRelationResource(String xml, String id) {
    RelationContext context = new RelationContext(id);
    parseXml(xml, new RelationVisitor(context));

    Reference typeRef = getRelationTypeReference(context.typeName);
    Reference sourceRef = references.get(context.sourceId);
    Reference targetRef = references.get(context.targetId);

    if (typeRef != null && sourceRef != null && targetRef != null) {
      RelationDTO relation = new RelationDTO();
      relation.setTypeName(context.typeName);
      relation.setSourceType(sourceRef.getType());
      relation.setSourceValue(sourceRef.getId());
      relation.setTargetType(targetRef.getType());
      relation.setTargetValue(targetRef.getId());
      return relation;
    } else {
      log("Error in %s: %s --> %s%n", context.typeName, context.sourceId, context.targetId);
      return null;
    }
  }

  private Reference getRelationTypeReference(String name) {
    return relationTypeNames.contains(name) ? new Reference(RelationType.class, name) : null;
  }

  private class RelationContext extends XmlContext {
    public String id;
    public String typeName = "";
    public String sourceId = "";
    public String targetId = "";

    public RelationContext(String id) {
      this.id = id;
    }

    public void error(String format, Object... args) {
      System.err.printf("## [%s] %s%n", id, String.format(format, args));
    }
  }

  private class RelationVisitor extends DelegatingVisitor<RelationContext> {
    public RelationVisitor(RelationContext context) {
      super(context);
      setDefaultElementHandler(new DefaultRelationHandler());
      addElementHandler(new RelationIdHandler(), "RelationId");
      addElementHandler(new RelationLinkHandler(), "Reference");
      addElementHandler(new RelationTypeHandler(), "Type");
      addElementHandler(new RelationActiveHandler(), "Active");
      addElementHandler(new RelationPassiveHandler(), "Passive");
    }
  }

  private class DefaultRelationHandler extends DefaultElementHandler<RelationContext> {
    private final Set<String> ignoredNames = Sets.newHashSet("arcadia", "relation", "request", "responseDate");

    @Override
    public Traversal enterElement(Element element, RelationContext context) {
      String name = element.getName();
      if (!ignoredNames.contains(name)) {
        context.error("Unexpected element: %s", name);
      }
      return Traversal.NEXT;
    }
  }

  private class RelationIdHandler extends CaptureHandler<RelationContext> {
    @Override
    public void handleContent(Element element, RelationContext context, String text) {
      if (!context.id.equals(text)) {
        context.error("ID mismatch: %s", text);
      }
    }
  }

  private class RelationLinkHandler extends CaptureHandler<RelationContext> {
    @Override
    public void handleContent(Element element, RelationContext context, String text) {
      context.error("Unexpected reference: %s", text);
    }
  }

  private class RelationTypeHandler extends CaptureHandler<RelationContext> {
    @Override
    public void handleContent(Element element, RelationContext context, String text) {
      if (text.equalsIgnoreCase("translation of")) {
        context.typeName = "hasTranslation";
      } else if (text.equalsIgnoreCase("comments on")) { // OK
        context.typeName = "hasEdition";
      } else if (text.equalsIgnoreCase("created by")) { // OK
        context.typeName = "isCreatedBy";
      } else if (text.equalsIgnoreCase("pseudonym")) {
        context.typeName = "isPseudonymOf";
      } else {
        context.error("Unexpected relation type: '%s'", text);
        System.exit(0);
      }
    }
  }

  private class RelationActiveHandler extends CaptureHandler<RelationContext> {
    @Override
    public void handleContent(Element element, RelationContext context, String text) {
      context.sourceId = text;
    }
  }

  private class RelationPassiveHandler extends CaptureHandler<RelationContext> {
    @Override
    public void handleContent(Element element, RelationContext context, String text) {
      context.targetId = text;
    }
  }

}
