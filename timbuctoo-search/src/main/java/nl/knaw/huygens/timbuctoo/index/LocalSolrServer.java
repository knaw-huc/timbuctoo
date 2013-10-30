package nl.knaw.huygens.timbuctoo.index;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

import nl.knaw.huygens.timbuctoo.config.Configuration;

import org.apache.commons.lang.StringUtils;
import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.SolrQuery.SortClause;
import org.apache.solr.client.solrj.SolrServer;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.embedded.EmbeddedSolrServer;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.common.SolrInputDocument;
import org.apache.solr.core.CoreContainer;
import org.apache.solr.core.CoreDescriptor;
import org.apache.solr.core.SolrCore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.Maps;
import com.google.inject.Inject;
import com.google.inject.Singleton;

/**
 * Handles communication with an embedded Solr server with various cores.
 * Existing cores that are not referred to are ignored.
 */
@Singleton
public class LocalSolrServer {

  private static final Logger LOG = LoggerFactory.getLogger(LocalSolrServer.class);

  // FIXME this is probably suboptimal:
  private static final int ROWS = 20000;
  private static final int FACET_LIMIT = 10000;

  private static final String ID_FIELD = "id";
  private static final String ALL = "*:*";

  private final CoreContainer container;
  private final Map<String, SolrServer> solrServers = Maps.newTreeMap();
  private final String solrHomeDir;
  private final int commitWithin;

  @Inject
  public LocalSolrServer(Configuration config) {
    solrHomeDir = config.getSolrHomeDir();
    LOG.info("Solr directory: {}", solrHomeDir);

    commitWithin = config.getIntSetting("solr.commit_within", 10 * 1000);
    LOG.info("Maximum time before a commit: {} seconds", commitWithin / 1000);

    try {
      File configFile = new File(new File(solrHomeDir, "conf"), "solr.xml");
      container = new CoreContainer(solrHomeDir, configFile);
    } catch (FileNotFoundException e) {
      throw new RuntimeException(e);
    }
  }

  public String addCore(String scopeName, String collectionName) {
    String coreName = String.format("%s.%s", scopeName, collectionName);
    String dataDir = String.format("%s/%s", scopeName, collectionName);
    String schemaName = getSchemaName(collectionName);

    CoreDescriptor descriptor = new CoreDescriptor(container, coreName, solrHomeDir);
    descriptor.setSchemaName(schemaName);
    descriptor.setDataDir(dataDir);
    descriptor.setLoadOnStartup(true);

    SolrCore core = container.create(descriptor);
    container.register(coreName, core, true);
    SolrServer server = new EmbeddedSolrServer(container, coreName);

    solrServers.put(coreName, server);
    return coreName;
  }

  /**
   * Returns the name of the schema file for the specified collection.
   * If a custom schema file exists it will be used, otherwise the
   * schema {@code file schema-tmpl.xml} will be used.
   */
  private String getSchemaName(String collectionName) {
    String schemaName = String.format("schema-%s.xml", collectionName);
    if (new File(new File(solrHomeDir, "conf"), schemaName).isFile()) {
      LOG.info("Schema for {} index: {}", collectionName, schemaName);
      return schemaName;
    } else {
      return "schema-tmpl.xml";
    }
  }

  public void add(String core, SolrInputDocument doc) throws SolrServerException, IOException {
    serverFor(core).add(doc, commitWithin);
  }

  public void deleteById(String core, String id) throws SolrServerException, IOException {
    serverFor(core).deleteById(id, commitWithin);
  }

  public void deleteById(String core, List<String> ids) throws SolrServerException, IOException {
    serverFor(core).deleteById(ids, commitWithin);
  }

  public void deleteByQuery(String core, String query) throws SolrServerException, IOException {
    serverFor(core).deleteByQuery(query, commitWithin);
  }

  public void deleteAll(String core) throws SolrServerException, IOException {
    serverFor(core).deleteByQuery(ALL, -1);
  }

  public void deleteAll() throws SolrServerException, IOException {
    for (String core : getCoreNames()) {
      LOG.info("Clearing {} index", core);
      deleteAll(core);
    }
  }

  public void commit(String core) throws SolrServerException, IOException {
    serverFor(core).commit();
    LOG.info("{} index: {} documents", core, count(core));
  }

  public void commitAll() throws SolrServerException, IOException {
    for (String core : getCoreNames()) {
      commit(core);
    }
  }

  public long count(String core) throws SolrServerException {
    SolrQuery params = new SolrQuery(ALL);
    params.setRows(0); // don't actually request any data
    return serverFor(core).query(params).getResults().getNumFound();
  }

  public QueryResponse search(String core, String query, Collection<String> facetFieldNames, String sortField) throws SolrServerException {
    SolrQuery solrQuery = new SolrQuery();
    solrQuery.setQuery(query);
    solrQuery.setFields(ID_FIELD);
    solrQuery.setRows(ROWS);
    solrQuery.addFacetField(facetFieldNames.toArray(new String[facetFieldNames.size()]));
    solrQuery.setFacetMinCount(0);
    solrQuery.setFacetLimit(FACET_LIMIT);
    solrQuery.setFilterQueries("!cache=false");
    solrQuery.setSort(new SortClause(sortField, SolrQuery.ORDER.asc));
    LOG.debug("Query: {}", solrQuery);
    return serverFor(core).query(solrQuery);
  }

  public QueryResponse getByIds(String core, List<String> ids, Collection<String> facetFieldNames, String sort) throws SolrServerException, IOException {
    return search(core, "id:(" + StringUtils.join(ids, " ") + ")", facetFieldNames, sort);
  }

  public void shutdown() {
    if (container != null) {
      container.shutdown();
    }
  }

  private Set<String> getCoreNames() {
    return solrServers.keySet();
  }

  private SolrServer serverFor(String core) {
    return solrServers.get(core);
  }

}
