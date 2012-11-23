package nl.knaw.huygens.repository.model.storage;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import nl.knaw.huygens.repository.model.Document;

@Target(value = { ElementType.TYPE })
@Retention(RetentionPolicy.RUNTIME)
public @interface RelatedDocument {
  Class<? extends Document> type();
  String[] accessors() default {};
}
