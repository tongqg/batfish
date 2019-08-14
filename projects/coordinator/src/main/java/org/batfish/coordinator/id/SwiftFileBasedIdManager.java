package org.batfish.coordinator.id;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.Nonnull;

import com.ebay.fwauto.excavator.openstack.SwiftClient;
import com.google.common.collect.ImmutableSet;

import org.batfish.common.BatfishException;
import org.batfish.identifiers.NetworkId;
import org.batfish.identifiers.SnapshotId;
import org.javaswift.joss.model.StoredObject;

public class SwiftFileBasedIdManager extends FileBasedIdManager {

    private SwiftClient client;

    private Path storageBase;

    // simple local cache
    // assume network never deleted
    private static Map<String, NetworkId> networkIdCache = new ConcurrentHashMap<String, NetworkId>();;
    // key is snapshot, assume snapshot name is global unique
    private static Map<String, SnapshotId> snapshotIdCache = new ConcurrentHashMap<String, SnapshotId>();;

    public SwiftFileBasedIdManager(Path storageBase) {
        super(storageBase);
        this.storageBase = storageBase;
        try {
            this.client = new SwiftClient(storageBase.getFileName().toString());
        } catch (IOException e) {
            throw new BatfishException(e.getMessage());
        }
    }

    protected String relativize(Path fullpath) {
        return storageBase.relativize(fullpath).toString();
    }

    private @Nonnull Set<String> listResolvableNames(String idsDir) {
        Collection<StoredObject> l = client.list(idsDir);
        return l.stream().filter(o -> o.getName().endsWith(ID_EXTENSION))
            .map(StoredObject::getName).map(name -> name.substring(
                name.lastIndexOf("/")+1, name.length() - ID_EXTENSION.length()))
                    .collect(ImmutableSet.toImmutableSet());
    }

    @Override
    public NetworkId getNetworkId(String network) {
        if (!networkIdCache.containsKey(network)) {
            if (!hasNetworkId(network)) {
                throw new IllegalArgumentException(String.format("No ID assigned to non-existent network %s", network));
            }
            try {
                NetworkId nid = new NetworkId(client.getString(relativize(getNetworkIdPath(network))));
                networkIdCache.put(network, nid);
            } catch (IOException e) {
                throw new BatfishException(e.getMessage());
            }
        }
        return networkIdCache.get(network);
    }

    @Override
    public boolean hasNetworkId(String network) {
        boolean exists = false;
        if (networkIdCache.containsKey(network)) {
            exists = true;
        } else {
            exists = client.exists(relativize(getNetworkIdPath(network)));
        }
        return exists;
    }

    @Override
    public Set<String> listNetworks() {
        return listResolvableNames(relativize(getNetworkIdsDir()));
    }

    @Override
    public SnapshotId getSnapshotId(String snapshot, NetworkId networkId) {
        if (! snapshotIdCache.containsKey(snapshot)) {
            if (!hasSnapshotId(snapshot, networkId)) {
                throw new IllegalArgumentException(
                    String.format("No ID assigned to non-existent snapshot '%s'", snapshot));
            }
            try {
                SnapshotId sid = new SnapshotId(client.getString(relativize(getSnapshotIdPath(snapshot, networkId))));
                snapshotIdCache.put(snapshot, sid);
            } catch (IOException e) {
                throw new BatfishException(e.getMessage());
            }
        }
        return snapshotIdCache.get(snapshot);
    }


    @Override
    public boolean hasSnapshotId(String snapshot, NetworkId networkId) {
        if (snapshotIdCache.containsKey(snapshot)) {
            return true;
        } else {
            return client.exists(relativize(getSnapshotIdPath(snapshot, networkId)));
        }
    }

    @Override
    public Set<String> listSnapshots(NetworkId networkId) {
        return listResolvableNames(relativize(getSnapshotIdsDir(networkId)));
    }

    @Override
    public void assignNetwork(String network, NetworkId networkId) {
        String path = relativize(getNetworkIdPath(network));
        client.setString(path, networkId.getId());
    }
    
    @Override
    public void assignSnapshot(String snapshot, NetworkId networkId, SnapshotId snapshotId) {
        String idFile = relativize(getSnapshotIdPath(snapshot, networkId));
        client.setString(idFile, snapshotId.getId());
    }

    @Override
    public void deleteSnapshot(String snapshot, NetworkId networkId) {
        client.delete(relativize(getSnapshotIdPath(snapshot, networkId)));
    }
}