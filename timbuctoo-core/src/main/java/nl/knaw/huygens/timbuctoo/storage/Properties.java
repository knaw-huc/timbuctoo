package nl.knaw.huygens.timbuctoo.storage;

/*
 * #%L
 * Timbuctoo core
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

import static com.google.common.base.Preconditions.checkArgument;
import static nl.knaw.huygens.timbuctoo.config.TypeNames.getInternalName;

import java.lang.reflect.Field;
import java.util.Collection;
import java.util.Map;
import java.util.TreeMap;

import nl.knaw.huygens.timbuctoo.model.util.Datable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Properties extends TreeMap<String, Object> {

  /** Separator between parts of a property name. */
  public static final String SEPARATOR = ":";

  /**
   * Returns the name of a property from its parts.
   */
  public static String propertyName(Class<?> type, String field) {
    return propertyName(getInternalName(type), field);
  }

  /**
   * Returns the name of a property from its parts.
   */
  public static String propertyName(String prefix, String field) {
    checkArgument(field != null && field.length() != 0);
    return Character.isLetter(field.charAt(0)) ? prefix + SEPARATOR + field : field;
  }

  // ---------------------------------------------------------------------------

  private static final long serialVersionUID = 1L;

  private static final Logger LOG = LoggerFactory.getLogger(Properties.class);

  public Properties() {}

  public Properties(Object object, Class<?> type) {
    this(object, FieldMap.getInstance(type));
  }

  public Properties(Object object, FieldMap fieldMap) {
    if (object != null) {
      for (Map.Entry<String, Field> entry : fieldMap.entrySet()) {
        try {
          Field field = entry.getValue();
          Object value = convertToSerializable(field.getType(), field.get(object));
          if (value != null) {
            put(entry.getKey(), value);
          }
        } catch (Exception e) {
          LOG.error("Error for field '{}'", entry.getValue());
          throw new RuntimeException(e);
        }
      }
    }
  }

  /**
   * Converts a property value to a value that can be serialized to Json.
   */
  private Object convertToSerializable(Class<?> type, Object value) {
    if (value == null) {
      return null;
    } else if (type == Datable.class) {
      return Datable.class.cast(value).getEDTF();
    } else if (Collection.class.isAssignableFrom(type)) {
      Collection<?> collection = Collection.class.cast(value);
      if (collection.isEmpty()) {
        return null;
      }
    }
    // Assume Jackson can handle it
    return value;
  }

}
