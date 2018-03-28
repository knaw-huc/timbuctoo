package nl.knaw.huygens.timbuctoo.v5.graphql.mutations;

import graphql.schema.DataFetcher;
import graphql.schema.DataFetchingEnvironment;
import nl.knaw.huygens.timbuctoo.util.Tuple;
import nl.knaw.huygens.timbuctoo.v5.dataset.DataSetRepository;
import nl.knaw.huygens.timbuctoo.v5.dataset.dto.DataSetMetaData;
import nl.knaw.huygens.timbuctoo.v5.dataset.exceptions.NotEnoughPermissionsException;
import nl.knaw.huygens.timbuctoo.v5.security.dto.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

public class DeleteDataSetDataFetcher implements DataFetcher {


  private static final Logger LOG = LoggerFactory.getLogger(DeleteDataSetDataFetcher.class);
  private final DataSetRepository dataSetRepository;

  public DeleteDataSetDataFetcher(DataSetRepository dataSetRepository) {
    this.dataSetRepository = dataSetRepository;
  }

  @Override
  public Object get(DataFetchingEnvironment environment) {
    String combinedId = environment.getArgument("dataSetId");
    Tuple<String, String> userAndDataSet = DataSetMetaData.splitCombinedId(combinedId);
    User user = MutationHelpers.getUser(environment);

    try {
      dataSetRepository.removeDataSet(userAndDataSet.getLeft(), userAndDataSet.getRight(), user);
      return new RemovedDataSet(combinedId);
    } catch (IOException e) {
      LOG.error("Data set deletion exception", e);
      throw new RuntimeException("Data set could not be deleted");
    } catch (NotEnoughPermissionsException e) {
      throw new RuntimeException("You do not have enough permissions");
    }
  }

  public static class RemovedDataSet {
    private final String dataSetId;

    public RemovedDataSet(String dataSetId) {
      this.dataSetId = dataSetId;
    }

    public String getDataSetId() {
      return dataSetId;
    }
  }
}
