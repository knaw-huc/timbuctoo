package nl.knaw.huygens.timbuctoo.storage;

import nl.knaw.huygens.timbuctoo.config.Configuration;
import nl.knaw.huygens.timbuctoo.model.SystemEntity;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * A class that writes and reads json files.
 */
public class JsonFileHandler {

  public JsonFileHandler(Configuration configMock, ObjectMapper objectMapperMock) {
    // TODO Auto-generated constructor stub
  }

  public <T extends SystemEntity> String addSystemEntity(Class<T> type, T entity) {
    // TODO Auto-generated method stub
    return null;
  }

}
