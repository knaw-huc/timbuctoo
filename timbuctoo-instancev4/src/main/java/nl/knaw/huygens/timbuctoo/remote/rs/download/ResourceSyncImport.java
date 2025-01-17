package nl.knaw.huygens.timbuctoo.remote.rs.download;

import com.google.common.base.Charsets;
import com.google.common.collect.Sets;
import nl.knaw.huygens.timbuctoo.remote.rs.download.ResourceSyncFileLoader.RemoteFilesList;
import nl.knaw.huygens.timbuctoo.remote.rs.download.exceptions.CantRetrieveFileException;
import nl.knaw.huygens.timbuctoo.remote.rs.exceptions.CantDetermineDataSetException;
import nl.knaw.huygens.timbuctoo.v5.dataset.ImportManager;
import nl.knaw.huygens.timbuctoo.v5.dataset.ImportStatus;
import nl.knaw.huygens.timbuctoo.v5.dataset.dto.DataSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.core.MediaType;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Future;

public class ResourceSyncImport {
  public static final Logger LOG = LoggerFactory.getLogger(ResourceSyncFileLoader.class);
  private ResourceSyncFileLoader resourceSyncFileLoader;
  private DataSet dataSet;
  private boolean async;

  public ResourceSyncImport(ResourceSyncFileLoader resourceSyncFileLoader, DataSet dataSet, boolean async) {
    this.resourceSyncFileLoader = resourceSyncFileLoader;
    this.dataSet = dataSet;
    this.async = async;
  }

  public ResourceSyncReport filterAndImport(String capabilityListUri, String userSpecifiedDataSet, boolean update,
                                            String authString)
    throws CantDetermineDataSetException, IOException, CantRetrieveFileException {
    List<RemoteFile> filesToImport;

    if (userSpecifiedDataSet == null) {
      filesToImport = filter(capabilityListUri, update, authString);
    } else {
      filesToImport = filter(capabilityListUri, userSpecifiedDataSet, authString);
    }

    if (update) {
      dataSet.getMetadata().getImportInfo().get(0).setLastImportedOn(Date.from(Instant.now()));
    }

    Iterator<RemoteFile> files = filesToImport.iterator();

    LOG.info("Found files '{}'", files.hasNext());

    if (!files.hasNext()) {
      LOG.error("No supported files available for import.");
      return new ResourceSyncReport();
    }

    ImportManager importManager = dataSet.getImportManager();

    ResourceSyncReport resourceSyncReport = new ResourceSyncReport();

    try {
      while (files.hasNext()) {
        RemoteFile file = files.next();
        MediaType parsedMediatype = MediaType.APPLICATION_OCTET_STREAM_TYPE;
        try {
          parsedMediatype = MediaType.valueOf(file.getMimeType());
        } catch (IllegalArgumentException e) {
          LOG.error("Failed to get mediatype", e);
        }
        if (importManager.isRdfTypeSupported(parsedMediatype)) {
          resourceSyncReport.importedFiles.add(file.getUrl());
          Future<ImportStatus> importedFileFuture = importManager.addLog(
            dataSet.getMetadata().getBaseUri(),
            dataSet.getMetadata().getGraph(),
            file.getUrl().substring(file.getUrl().lastIndexOf('/') + 1),
            file.getData().get(),
            Optional.of(Charsets.UTF_8),
            parsedMediatype
          );
          //TODO: Make synchronous import more efficient
          //Currently synchronous import is to be used only for testing purposes.
          if (!async) {
            importedFileFuture.get();
          }
        } else {
          resourceSyncReport.ignoredFiles.add(file.getUrl());
          importManager.addFile(
            file.getData().get(),
            file.getUrl(),
            parsedMediatype
          );
        }
      }
      return resourceSyncReport;
    } catch (Exception e) {
      LOG.error("Could not read files to import", e);
      return resourceSyncReport;
    }
  }

  private List<RemoteFile> filter(String capabilityListUri, boolean update, String authString)
    throws CantDetermineDataSetException,
    IOException,
    CantRetrieveFileException {
    try {
      RemoteFilesList remoteFilesList = resourceSyncFileLoader.getRemoteFilesList(capabilityListUri, authString);

      List<RemoteFile> resources = new ArrayList<>();

      if (!remoteFilesList.getChangeList().isEmpty()) {
        if (update) {
          Date lastUpdate = dataSet.getMetadata().getImportInfo().get(0).getLastImportedOn();

          for (RemoteFile remoteFile : remoteFilesList.getChangeList()) {
            if (remoteFile.getMetadata().getDateTime().after(lastUpdate)) {
              resources.add(remoteFile);
            }
          }

        } else {
          resources.addAll(remoteFilesList.getChangeList());
        }

        return resources;
      }

      for (RemoteFile remoteFile : remoteFilesList.getResourceList()) {
        if (remoteFile.getMetadata().isDataset()) {
          resources.add(remoteFile);
          return resources;
        }
      }

      if (remoteFilesList.getResourceList().size() == 1) {
        resources.add(remoteFilesList.getResourceList().get(0));
      } else {
        throw new CantDetermineDataSetException(remoteFilesList.getResourceList());
      }

      return resources;

    } catch (IOException e) {
      throw e;
    }

  }

  private List<RemoteFile> filter(String capabilityListUri, String userSpecifiedDataSet, String authString)
    throws CantRetrieveFileException {
    try {
      RemoteFilesList remoteFilesList = resourceSyncFileLoader.getRemoteFilesList(capabilityListUri, authString);

      List<RemoteFile> resources = new ArrayList<>();

      for (RemoteFile remoteFile : remoteFilesList.getResourceList()) {
        if (remoteFile.getUrl().equals(userSpecifiedDataSet)) {
          resources.add(remoteFile);
          break;
        }
      }

      return resources;

    } catch (IOException e) {
      return Collections.emptyList();
    }
  }

  public static class ResourceSyncReport {
    public final Set<String> importedFiles = Sets.newTreeSet();
    public final Set<String> ignoredFiles = Sets.newTreeSet();
  }
}
