package nl.knaw.huygens.timbuctoo.database.dto.property;

import nl.knaw.huygens.timbuctoo.model.properties.ReadableProperty;
import nl.knaw.huygens.timbuctoo.database.dto.dataset.Collection;
import nl.knaw.huygens.timbuctoo.util.Tuple;

import java.io.IOException;
import java.util.Optional;

public abstract class PropertyConverter<TypeT> {

  private final Collection collection;

  public PropertyConverter(Collection collection) {
    this.collection = collection;
  }

  public final TimProperty<?> from(String propertyName, TypeT value) throws UnknownPropertyException, IOException {
    Optional<ReadableProperty> property = collection.getProperty(propertyName);
    if (!property.isPresent()) {
      throw new UnknownPropertyException(String.format(
        "Collection with name '%s' does not contain property with name '%s'",
        collection.getCollectionName(),
        propertyName
      ));
    }


    switch (property.get().getUniqueTypeId()) {
      case "altnames":
        return createAltNamesProperty(propertyName, value);
      case "datable":
        return createDatableProperty(propertyName, value);
      case "default-person-display-name":
        return createDefaultFullPersonNameProperty(propertyName, value);
      case "default-location-display-name":
        return createDefaultLocationNameProperty(propertyName, value);
      case "hyperlinks":
        return createHyperLinksProperty(propertyName, value);
      case "person-names":
        return createPersonNamesProperty(propertyName, value);
      case "encoded-array-of-limited-values":
        return createArrayOfLimitedValuesProperty(propertyName, value);
      case "encoded-string-of-limited-values":
        return createEncodedStringOfLimitedValuesProperty(propertyName, value);
      case "string":
        return createStringProperty(propertyName, value);
      case "unencoded-string-of-limited-values":
        return createStringOfLimitedValues(propertyName, value);
      default:
        throw new UnknownPropertyException(String.format(
          "Property '%s' of collection '%s' has unknown type '%s'",
          propertyName,
          collection.getCollectionName(),
          property.get().getUniqueTypeId()
        ));
    }
  }

  protected abstract AltNamesProperty createAltNamesProperty(String propertyName, TypeT value) throws IOException;

  protected abstract DatableProperty createDatableProperty(String propertyName, TypeT value) throws IOException;

  protected abstract DefaultFullPersonNameProperty createDefaultFullPersonNameProperty(String propertyName, TypeT value)
    throws IOException;

  protected abstract DefaultLocationNameProperty createDefaultLocationNameProperty(String propertyName, TypeT value)
    throws IOException;

  protected abstract HyperLinksProperty createHyperLinksProperty(String propertyName, TypeT value) throws IOException;

  protected abstract PersonNamesProperty createPersonNamesProperty(String propertyName, TypeT value) throws IOException;

  protected abstract ArrayOfLimitedValuesProperty createArrayOfLimitedValuesProperty(String propertyName, TypeT value)
    throws IOException;

  protected abstract EncodedStringOfLimitedValuesProperty createEncodedStringOfLimitedValuesProperty(
    String propertyName, TypeT value) throws IOException;

  protected abstract StringProperty createStringProperty(String propertyName, TypeT value) throws IOException;

  protected abstract StringOfLimitedValuesProperty createStringOfLimitedValues(String propertyName, TypeT value)
    throws IOException;

  protected abstract Tuple<String, TypeT> to(AltNamesProperty property) throws IOException;

  protected abstract Tuple<String, TypeT> to(DatableProperty property) throws IOException;

  protected abstract Tuple<String, TypeT> to(DefaultFullPersonNameProperty property) throws IOException;

  protected abstract Tuple<String, TypeT> to(DefaultLocationNameProperty property) throws IOException;

  protected abstract Tuple<String, TypeT> to(HyperLinksProperty property) throws IOException;

  protected abstract Tuple<String, TypeT> to(PersonNamesProperty property) throws IOException;

  protected abstract Tuple<String, TypeT> to(ArrayOfLimitedValuesProperty property)
    throws IOException;

  protected abstract Tuple<String, TypeT> to(EncodedStringOfLimitedValuesProperty property) throws IOException;

  protected abstract Tuple<String, TypeT> to(StringProperty property) throws IOException;

  protected abstract Tuple<String, TypeT> to(StringOfLimitedValuesProperty property) throws IOException;

  protected IOException readOnlyProperty(String propertyName, Class<? extends TimProperty> propertyType) {
    return new IOException(String.format(
      "'%s' of type '%s' cannot be stored, because it is a read-only property.",
      propertyName,
      propertyType
    ));
  }
}
