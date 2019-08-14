package org.batfish.storage;

import static org.batfish.common.plugin.PluginConsumer.DEFAULT_HEADER_LENGTH_BYTES;
import static org.batfish.common.plugin.PluginConsumer.detectFormat;

import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.PushbackInputStream;
import java.io.Serializable;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Map;
import java.util.Map.Entry;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.zip.GZIPInputStream;

import javax.annotation.Nonnull;

import com.ebay.fwauto.excavator.openstack.SwiftClient;
import com.ebay.fwauto.excavator.openstack.SwiftUtil;
import com.google.common.io.Closer;

import org.batfish.common.BatfishException;
import org.batfish.common.BatfishLogger;
import org.batfish.common.BfConsts;
import org.batfish.common.plugin.PluginConsumer.Format;
import org.batfish.common.util.BatfishObjectMapper;
import org.batfish.common.util.CommonUtil;
import org.batfish.datamodel.Configuration;
import org.batfish.datamodel.SnapshotMetadata;
import org.batfish.datamodel.answers.ConvertConfigurationAnswerElement;
import org.batfish.identifiers.NetworkId;
import org.batfish.identifiers.SnapshotId;
import org.javaswift.joss.model.StoredObject;

import net.jpountz.lz4.LZ4FrameInputStream;
import net.jpountz.lz4.LZ4FrameOutputStream;

public class SwiftFileBasedStorage extends FileBasedStorage {

    private SwiftClient client;

    private static Map<String, Boolean> networkIdCache = new ConcurrentHashMap<String, Boolean>();

    public SwiftFileBasedStorage(Path baseDir, BatfishLogger logger) {
        super(baseDir, logger);
        try {
            client = new SwiftClient(baseDir.getFileName().toString());
        } catch (IOException e) {
            throw new BatfishException(e.getMessage());
        }
    }

    private @Nonnull String getNetworkDir(NetworkId networkId) {
        return networkId.getId();
    }

    private @Nonnull String getSnapshotDir(NetworkId networkId, SnapshotId snapshotId) {
        return SwiftUtil.resolvePath(getNetworkDir(networkId), BfConsts.RELPATH_SNAPSHOTS_DIR,
                snapshotId.getId());
    }

    private @Nonnull String getSnapshotMetadataPath(NetworkId networkId, SnapshotId snapshotId) {
        return SwiftUtil.resolvePath(getSnapshotDir(networkId, snapshotId), BfConsts.RELPATH_OUTPUT,
                BfConsts.RELPATH_METADATA_FILE);
    }

    private @Nonnull String getVendorIndependentConfigDir(NetworkId network, SnapshotId snapshot) {
        return SwiftUtil.resolvePath(getSnapshotDir(network, snapshot), BfConsts.RELPATH_OUTPUT,
                BfConsts.RELPATH_VENDOR_INDEPENDENT_CONFIG_DIR);
    }

    private <S extends Serializable> SortedMap<String, S> deserializeObjects(
            Map<String, String> namesByPath, Class<S> outputClass) {
        String outputClassName = outputClass.getName();
        AtomicInteger completed = _newBatch.apply(
                String.format("Deserializing objects of type '%s' from files", outputClassName),
                namesByPath.size());
        return new TreeMap<>(namesByPath.entrySet().parallelStream()
                .collect(Collectors.toMap(Entry::getValue, entry -> {
                    String inputPath = entry.getKey();
                    String name = entry.getValue();
                    _logger.debugf("Reading %s '%s' from '%s'\n", outputClassName, name, inputPath);
                    S output = deserializeObject(inputPath, outputClass);
                    completed.incrementAndGet();
                    return output;
                })));
    }

    private <S extends Serializable> S deserializeObject(String inputPath, Class<S> outputClass)
            throws BatfishException {
        try (Closer closer = Closer.create()) {
            InputStream fis = closer.register(client.getInputStream(inputPath));
            PushbackInputStream pbstream = new PushbackInputStream(fis,
                    DEFAULT_HEADER_LENGTH_BYTES);
            Format f = detectFormat(pbstream);
            ObjectInputStream ois;
            if (f == Format.GZIP) {
                GZIPInputStream gis = closer
                        .register(new GZIPInputStream(pbstream, 8192 /* enlarge buffer */));
                ois = new ObjectInputStream(gis);
            }
            else if (f == Format.LZ4) {
                LZ4FrameInputStream lis = closer.register(new LZ4FrameInputStream(pbstream));
                ois = new ObjectInputStream(lis);
            }
            else if (f == Format.JAVA_SERIALIZED) {
                ois = new ObjectInputStream(pbstream);
            }
            else {
                throw new BatfishException(
                        String.format("Could not detect format of the file %s", inputPath));
            }
            closer.register(ois);
            return outputClass.cast(ois.readObject());
        }
        catch (Exception e) {
            throw new BatfishException(
                    String.format("Failed to deserialize object of type %s from file %s",
                            outputClass.getCanonicalName(), inputPath),
                    e);
        }
    }

    @Override
    public SortedMap<String, Configuration> loadConfigurations(NetworkId network,
            SnapshotId snapshot) {
        String indepDir = getVendorIndependentConfigDir(network, snapshot);
        _logger.info("\n*** DESERIALIZING VENDOR-INDEPENDENT CONFIGURATION STRUCTURES ***\n");
        Map<String, String> namesByPath = new TreeMap<>();
        Collection<StoredObject> list = client.list(indepDir);
        for (StoredObject object : list) {
            String path = object.getName();
            namesByPath.put(path, SwiftUtil.getNameInPath(path));
        }
        try {
            return deserializeObjects(namesByPath, Configuration.class);
        }
        catch (BatfishException e) {
            return null;
        }
    }

    private void serializeObject(Serializable object, String outputPath) {
        try {
            try (ByteArrayOutputStream out = new ByteArrayOutputStream();
                    LZ4FrameOutputStream gos = new LZ4FrameOutputStream(out);
                    ObjectOutputStream oos = new ObjectOutputStream(gos)) {
                oos.writeObject(object);
                oos.flush();
                client.set(outputPath, out.toByteArray());
            }
        }
        catch (Throwable e) {
            throw new BatfishException("Failed to serialize object to output file: " + outputPath,
                    e);
        }
    }

    @Override
    public void storeConfigurations(Map<String, Configuration> configurations,
            ConvertConfigurationAnswerElement convertAnswerElement, NetworkId network,
            SnapshotId snapshot) {
        Path ccaePath = getConvertAnswerPath(network, snapshot);
        mkdirs(ccaePath);
        CommonUtil.deleteIfExists(ccaePath);
        FileBasedStorage.serializeObject(convertAnswerElement, ccaePath);

        String batchName = String.format(
                "Serializing %s vendor-independent configuration structures for snapshot %s",
                configurations.size(), snapshot);
        _logger.infof("\n*** %s***\n", batchName.toUpperCase());
        AtomicInteger progressCount = _newBatch.apply(batchName, configurations.size());

        String outputDir = getVendorIndependentConfigDir(network, snapshot);
        configurations.entrySet().parallelStream().forEach(e -> {
            String currentOutputPath = SwiftUtil.resolvePath(outputDir, e.getKey());
            serializeObject(e.getValue(), currentOutputPath);
            progressCount.incrementAndGet();
        });
    }

    @Override
    public boolean checkNetworkExists(NetworkId network) {
        if (networkIdCache.containsKey(network.getId())){
            return true;
        }
        boolean exists = client.exists(network.getId());
        if (exists) {
            networkIdCache.put(network.getId(), exists);
        }
        return exists;
    }

    @Override
    public void storeSnapshotMetadata(SnapshotMetadata snapshotMetadata, NetworkId networkId,
            SnapshotId snapshotId) throws IOException {
        client.setString(getSnapshotMetadataPath(networkId, snapshotId), BatfishObjectMapper.writeString(snapshotMetadata));
    }

    @Override
    public String loadSnapshotMetadata(NetworkId networkId, SnapshotId snapshotId)
            throws FileNotFoundException, IOException {
        return client.getString(getSnapshotMetadataPath(networkId, snapshotId));
    }

    @Override
    public void initNetwork(NetworkId networkId) {
        try {
            client.mkdir(networkId.getId());
        } catch (Exception e) {
            throw new BatfishException(e.getMessage());

        }
    }

}
