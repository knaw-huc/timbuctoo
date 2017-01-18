package nl.knaw.huygens.timbuctoo.security.dataaccess.azure;

import com.google.common.collect.ImmutableMap;
import com.microsoft.azure.storage.StorageException;
import com.microsoft.azure.storage.table.CloudTableClient;
import com.microsoft.azure.storage.table.DynamicTableEntity;
import com.microsoft.azure.storage.table.EntityProperty;
import nl.knaw.huygens.timbuctoo.security.dataaccess.VreAuthorizationAccess;
import nl.knaw.huygens.timbuctoo.security.dto.VreAuthorization;
import nl.knaw.huygens.timbuctoo.security.exceptions.AuthorizationException;
import nl.knaw.huygens.timbuctoo.security.exceptions.AuthorizationUnavailableException;
import org.slf4j.Logger;

import java.util.Map;
import java.util.Optional;

import static org.slf4j.LoggerFactory.getLogger;

public class AzureVreAuthorizationAccess extends AzureAccess implements VreAuthorizationAccess {

  private static final Logger LOG = getLogger(AzureVreAuthorizationAccess.class);

  public AzureVreAuthorizationAccess(CloudTableClient client) throws AzureAccessNotPossibleException {
    super(client, "vreAuthorization");
  }

  public VreAuthorization propsToObject(DynamicTableEntity entity) {
    return VreAuthorization.create(
      getStringOrNull(entity, "vreId"),
      getStringOrNull(entity, "userId"),
      getStringArrayOrEmpty(entity, "roles")
    );
  }

  public Map<String, EntityProperty> objectToProps(VreAuthorization source) {
    return ImmutableMap.of(
      "vreId", new EntityProperty(source.getVreId()),
      "userId", new EntityProperty(source.getUserId()),
      "roles", new EntityProperty(String.join(",", source.getRoles()))
    );
  }

  @Override
  public VreAuthorization getOrCreateAuthorization(String vreId, String userId, String userRole)
    throws AuthorizationUnavailableException {
    Optional<VreAuthorization> curAuthorization = getAuthorization(vreId, userId);
    if (curAuthorization.isPresent()) {
      return curAuthorization.get();
    } else {
      VreAuthorization auth = VreAuthorization.create(vreId, userId, userRole);
      try {
        create(vreId, userId, objectToProps(auth));
        return auth;
      } catch (StorageException e) {
        LOG.error("createAuthorization failed", e);
        throw new AuthorizationUnavailableException("Could not add authorization");
      }
    }
  }

  @Override
  public Optional<VreAuthorization> getAuthorization(String vreId, String userId)
    throws AuthorizationUnavailableException {
    try {
      return retrieve(vreId, userId).map(this::propsToObject);
    } catch (StorageException e) {
      LOG.error("getAuthorization failed", e);
      throw new AuthorizationUnavailableException("Could not get authorization");
    }
  }

  @Override
  public void deleteVreAuthorizations(String vreId, String userId) throws AuthorizationException {
    try {
      final Optional<VreAuthorization> authorization = getAuthorization(vreId, userId);
      if (authorization.isPresent() && authorization.get().isAllowedToWrite()) {
        try {
          delete(vreId, userId);
        } catch (StorageException e) {
          LOG.error("deleteVreAuthorizations failed", e);
          throw new AuthorizationException("Could not delete authorization for vre '" + vreId + "'");
        }
      } else {
        throw new AuthorizationException("User with id '" + userId + "' is not allowed to delete vre '" + vreId + "'");
      }
    } catch (AuthorizationUnavailableException e) {
      throw new AuthorizationException(e.getMessage());
    }
  }
}
