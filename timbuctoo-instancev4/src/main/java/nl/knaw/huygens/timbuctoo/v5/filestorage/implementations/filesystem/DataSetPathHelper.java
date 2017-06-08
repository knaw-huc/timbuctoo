package nl.knaw.huygens.timbuctoo.v5.filestorage.implementations.filesystem;

import java.io.File;


public class DataSetPathHelper {
  private final File rootDir;

  /**
   * Creation of the class will also create the root directory.
   */
  public DataSetPathHelper(String rootDir) {
    this(new File(rootDir));
  }

  /**
   * Creation of the class will also create the root directory.
   */
  public DataSetPathHelper(File rootDir) {
    this.rootDir = rootDir;
    rootDir.mkdir();
  }

  /**
   * Creates a File object and creates the paths on the file system as well.
   */
  public File pathInDataSet(String userId, String dataSetId, String pathToCreate) {
    File dataSetPath = dataSetPath(userId, dataSetId);
    File path = createPathToFileSystem(dataSetPath, pathToCreate);
    return path;
  }

  /**
   * Creates the path of the data set but not that of the file.
   */
  public File fileInDataSet(String userId, String dataSetId, String fileName) {
    File dataSetPath = dataSetPath(userId, dataSetId);
    return new File(dataSetPath, fileName);
  }

  public File dataSetPath(String userId, String dataSetId) {
    File userPath = createPathToFileSystem(rootDir, userId);
    return createPathToFileSystem(userPath, dataSetId);
  }

  private File createPathToFileSystem(File parent, String pathToCreate) {
    File path = new File(parent, pathToCreate);
    path.mkdir();
    return path;
  }
}
