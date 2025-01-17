package nl.knaw.huygens.timbuctoo.v5.graphql.mutations;

import graphql.schema.DataFetchingEnvironment;
import nl.knaw.huygens.timbuctoo.remote.rs.download.ResourceSyncFileLoader;
import nl.knaw.huygens.timbuctoo.remote.rs.download.ResourceSyncImport;
import nl.knaw.huygens.timbuctoo.remote.rs.download.exceptions.CantRetrieveFileException;
import nl.knaw.huygens.timbuctoo.remote.rs.exceptions.CantDetermineDataSetException;
import nl.knaw.huygens.timbuctoo.util.Tuple;
import nl.knaw.huygens.timbuctoo.v5.dataset.DataSetRepository;
import nl.knaw.huygens.timbuctoo.v5.dataset.dto.DataSet;
import nl.knaw.huygens.timbuctoo.v5.dataset.dto.DataSetMetaData;
import nl.knaw.huygens.timbuctoo.v5.security.dto.Permission;
import nl.knaw.huygens.timbuctoo.v5.security.dto.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Optional;

public class ResourceSyncUpdateMutation extends Mutation {
  private static final Logger LOG = LoggerFactory.getLogger(ResourceSyncImportMutation.class);
  private final DataSetRepository dataSetRepository;
  private final ResourceSyncFileLoader resourceSyncFileLoader;

  public ResourceSyncUpdateMutation(Runnable schemaUpdater, DataSetRepository dataSetRepository,
                                    ResourceSyncFileLoader resourceSyncFileLoader) {
    super(schemaUpdater);
    this.dataSetRepository = dataSetRepository;
    this.resourceSyncFileLoader = resourceSyncFileLoader;
  }

  @Override
  public Object executeAction(DataFetchingEnvironment env) {
    User user = MutationHelpers.getUser(env);

    String combinedId = env.getArgument("dataSetId");
    // the user-specified authorization token for remote server:
    String authString = env.getArgument("authorization");
    Tuple<String, String> userAndDataSet = DataSetMetaData.splitCombinedId(combinedId);
    Optional<DataSet> dataSetOpt;

    ResourceSyncImport.ResourceSyncReport resourceSyncReport;

    try {
      dataSetOpt = dataSetRepository.getDataSet(user, userAndDataSet.getLeft(), userAndDataSet.getRight());
      if (!dataSetOpt.isPresent()) {
        LOG.error("DataSet does not exist.");
        throw new RuntimeException("DataSet does not exist.");
      }
      DataSet dataSet = dataSetOpt.get();
      MutationHelpers.checkPermission(env, dataSet.getMetadata(), Permission.UPDATE_RESOURCESYNC);
      ResourceSyncImport resourceSyncImport = new ResourceSyncImport(
        resourceSyncFileLoader, dataSet, false);
      String capabilityListUri = dataSet.getMetadata().getImportInfo().get(0).getImportSource();
      resourceSyncReport = resourceSyncImport.filterAndImport(capabilityListUri, null, true,
        authString);
    } catch (IOException | CantRetrieveFileException | CantDetermineDataSetException e) {
      LOG.error("Failed to do a resource sync import. ", e);
      throw new RuntimeException(e);
    }

    return resourceSyncReport;
  }
}
