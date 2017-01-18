package nl.knaw.huygens.timbuctoo.bulkupload.loaders.csv;

import nl.knaw.huygens.timbuctoo.bulkupload.InvalidFileException;
import nl.knaw.huygens.timbuctoo.bulkupload.parsingstatemachine.ImporterStubs;
import org.junit.Test;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public class CsvLoaderTest {

  @Test
  public void importCsv() throws Exception {
    File csvFile = new File(CsvLoaderTest.class.getResource("test.csv").toURI());
    CsvLoader loader = new CsvLoader("testcollection");
    try {
      List<String> results = new ArrayList<>();
      AtomicBoolean failure = new AtomicBoolean(false);
      loader.loadData(csvFile, ImporterStubs.withCustomReporter(logline -> {
        if (logline.matches("failure.*")) {
          failure.set(true);
        }
        results.add(logline);
      }));
      if (failure.get()) {
        throw new RuntimeException("Failure during import: \n" + String.join("\n", results));
      }
    } catch (InvalidFileException e) {
      throw new RuntimeException(e);
    }
  }
}
