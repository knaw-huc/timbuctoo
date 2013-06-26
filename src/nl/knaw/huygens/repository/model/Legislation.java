package nl.knaw.huygens.repository.model;

import nl.knaw.huygens.repository.annotations.DocumentTypeName;
import nl.knaw.huygens.repository.annotations.IDPrefix;
import nl.knaw.huygens.repository.annotations.IndexAnnotation;

import com.fasterxml.jackson.annotation.JsonIgnore;

@IDPrefix("LEG")
@DocumentTypeName("legislation")
public class Legislation extends DomainDocument {

  private String title;

  @Override
  @JsonIgnore
  @IndexAnnotation(fieldName = "desc")
  public String getDescription() {
    return title;
  }

  public String getTitle() {
    return title;
  }

  public void setTitle(String title) {
    this.title = title;
  }

}
