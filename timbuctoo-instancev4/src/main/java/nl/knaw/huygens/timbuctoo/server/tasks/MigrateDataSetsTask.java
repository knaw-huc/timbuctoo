package nl.knaw.huygens.timbuctoo.server.tasks;

import com.google.common.collect.ImmutableMultimap;
import io.dropwizard.servlets.tasks.Task;
import nl.knaw.huygens.timbuctoo.v5.dataset.DataSetRepository;
import nl.knaw.huygens.timbuctoo.v5.dataset.dto.DataSet;

import java.io.PrintWriter;

public class MigrateDataSetsTask extends Task {
  private final DataSetRepository dataSetRepository;

  public MigrateDataSetsTask(DataSetRepository dataSetRepository) {
    super("migrateDataSets");
    this.dataSetRepository = dataSetRepository;
  }

  @Override
  public void execute(ImmutableMultimap<String, String> parameters, PrintWriter output) throws Exception {
    for (DataSet dataSet : dataSetRepository.getDataSets()) {
      output.println("Migrate dataset: " + dataSet.getMetadata().getCombinedId());
      output.flush();
      dataSet.migrate();
      output.println("Finished migration of dataset: " + dataSet.getMetadata().getCombinedId());
      output.flush();
    }

  }
}
