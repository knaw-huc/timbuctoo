package nl.knaw.huygens.timbuctoo.search.description.facet;

import nl.knaw.huygens.timbuctoo.search.description.FacetDescription;
import nl.knaw.huygens.timbuctoo.search.description.PropertyParser;
import nl.knaw.huygens.timbuctoo.search.description.propertyparser.PropertyParserFactory;
import org.junit.Before;
import org.junit.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

public class FacetDescriptionFactoryTest {

  private FacetDescriptionFactory instance;
  private PropertyParserFactory parserFactory;

  @Before
  public void setUp() throws Exception {
    parserFactory = mock(PropertyParserFactory.class);
    instance = new FacetDescriptionFactory(parserFactory);
  }

  @Test
  public void createListFacetDescriptionCreatesAListFacetDescription() {
    PropertyParser parser = mock(PropertyParser.class);

    FacetDescription description = instance.createListFacetDescription("facetName", "propertyName", parser);

    assertThat(description, is(instanceOf(ListFacetDescription.class)));
  }

  @Test
  public void createListFacetLetsThePropertyParserFactoryCreateAParser() {
    FacetDescription description = instance.createListFacetDescription("facetName", "propertyName", String.class);

    verify(parserFactory).getParser(String.class);
  }
}