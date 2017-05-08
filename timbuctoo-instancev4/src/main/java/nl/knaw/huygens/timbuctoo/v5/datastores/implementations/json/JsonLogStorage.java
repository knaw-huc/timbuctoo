package nl.knaw.huygens.timbuctoo.v5.datastores.implementations.json;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Charsets;
import com.sleepycat.je.DatabaseException;
import nl.knaw.huygens.timbuctoo.v5.datastores.implementations.json.dto.LogStorage;
import nl.knaw.huygens.timbuctoo.v5.logprocessing.DataSetLogEntry;
import nl.knaw.huygens.timbuctoo.v5.logprocessing.LocalData;
import nl.knaw.huygens.timbuctoo.v5.logprocessing.LocalDataFile;
import nl.knaw.huygens.timbuctoo.v5.logprocessing.RdfCreator;
import nl.knaw.huygens.timbuctoo.v5.logprocessing.exceptions.LogStorageFailedException;
import nl.knaw.huygens.timbuctoo.v5.rdfreader.implementations.rdf4j.Rdf4jWriter;
import org.eclipse.rdf4j.rio.RDFFormat;
import org.slf4j.Logger;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.util.Iterator;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

import static org.slf4j.LoggerFactory.getLogger;

public class JsonLogStorage implements nl.knaw.huygens.timbuctoo.v5.logprocessing.datastore.LogStorage {

  private final File logIndex;
  private final File logLocation;
  private final Supplier<URI> logUriCreator;
  private static final Logger LOG = getLogger(JsonLogStorage.class);
  private final LogStorage data;
  private ObjectMapper mapper;

  public JsonLogStorage(File logIndex, File logLocation, Supplier<URI> logUriCreator, ObjectMapper objectMapper)
      throws DatabaseException, IOException {
    mapper = objectMapper;
    this.logIndex = logIndex;
    this.logLocation = logLocation;
    this.logUriCreator = logUriCreator;
    if (logIndex.exists()) {
      data = mapper.readValue(logIndex, LogStorage.class);
    } else {
      data = new LogStorage();
    }
  }

  private LocalDataFile createLogEntry(URI logUri, Optional<String> mimeType, Optional<Charset> charset)
      throws LogStorageFailedException {
    final LocalDataFile log;
    final String file = logUri.toASCIIString().replaceAll("[^a-zA-Z0-9_-]", "_") + UUID.randomUUID();
    try {
      log = new LocalDataFile(
        logUri,
        new File(logLocation, file),
        charset.map(Charset::name),
        mimeType
      );
      data.logEntries.add(log);
      mapper.writeValue(logIndex, data);
    } catch (IOException e) {
      throw new LogStorageFailedException(e);
    }
    return log;
  }

  @Override
  public LocalData getLog(URI logUri) {
    for (LocalDataFile logEntry : data.logEntries) {
      if (logEntry.getName().equals(logUri)) {
        return logEntry;
      }
    }
    return null;
  }

  @Override
  public LocalData saveLog(URI identifier, Optional<String> mimeType, Optional<Charset> charset, InputStream rdf)
      throws LogStorageFailedException {
    LocalDataFile logEntry = createLogEntry(identifier, mimeType, charset);
    try {
      Files.copy(rdf, logEntry.getFile().toPath());
      return logEntry;
    } catch (IOException e) {
      throw new LogStorageFailedException(e);
    }
  }

  @Override
  public LocalData startOrContinueAppendLog(RdfCreator generator) throws LogStorageFailedException {
    URI logUri = logUriCreator.get();//For now we never append
    LocalData log = getLog(logUri);
    if (log == null) {
      log = createLogEntry(logUri, Optional.of(RDFFormat.TURTLE.getDefaultMIMEType()), Optional.of(Charsets.UTF_8));
    }

    try {
      Rdf4jWriter writer = new Rdf4jWriter(log.getAppendingWriter(), RDFFormat.TURTLE);
      writer.start();
      generator.sendQuads(writer);
      writer.finish();
      return log;
    } catch (FileNotFoundException e) {
      throw new LogStorageFailedException(e);
    }

  }

  @Override
  public Iterable<? extends DataSetLogEntry> getLogsFrom(long currentVersion) {
    return () -> {
      int[] index = new int[] {0};
      return new Iterator<DataSetLogEntry>() {
        @Override
        public boolean hasNext() {
          return index[0] < data.logEntries.size();
        }

        @Override
        public DataSetLogEntry next() {
          int localIndex = index[0]++;
          return new DataSetLogEntry() {
            @Override
            public LocalData getData() {
              return data.logEntries.get(localIndex);
            }

            @Override
            public long getVersion() {
              return localIndex + 1;
            }
          };
        }
      };
    };
  }

}
