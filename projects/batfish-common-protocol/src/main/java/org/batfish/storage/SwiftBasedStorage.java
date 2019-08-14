
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
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiFunction;
import java.util.stream.Collectors;
import java.util.zip.GZIPInputStream;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;

import com.ebay.fwauto.excavator.openstack.SwiftClient;
import com.ebay.fwauto.excavator.openstack.SwiftUtil;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.google.common.base.Throwables;
import com.google.common.io.Closer;

import org.batfish.common.BatfishException;
import org.batfish.common.BatfishLogger;
import org.batfish.common.BfConsts;
import org.batfish.common.CompletionMetadata;
import org.batfish.common.NetworkSnapshot;
import org.batfish.common.plugin.PluginConsumer.Format;
import org.batfish.common.topology.Layer1Topology;
import org.batfish.common.topology.Layer2Topology;
import org.batfish.common.util.BatfishObjectMapper;
import org.batfish.datamodel.AnalysisMetadata;
import org.batfish.datamodel.Configuration;
import org.batfish.datamodel.SnapshotMetadata;
import org.batfish.datamodel.Topology;
import org.batfish.datamodel.answers.AnswerMetadata;
import org.batfish.datamodel.answers.ConvertConfigurationAnswerElement;
import org.batfish.datamodel.answers.MajorIssueConfig;
import org.batfish.datamodel.bgp.BgpTopology;
import org.batfish.datamodel.collections.NodeInterfacePair;
import org.batfish.datamodel.eigrp.EigrpTopology;
import org.batfish.datamodel.isp_configuration.IspConfiguration;
import org.batfish.datamodel.ospf.OspfTopology;
import org.batfish.datamodel.questions.Question;
import org.batfish.datamodel.vxlan.VxlanTopology;
import org.batfish.identifiers.AnalysisId;
import org.batfish.identifiers.AnswerId;
import org.batfish.identifiers.IssueSettingsId;
import org.batfish.identifiers.NetworkId;
import org.batfish.identifiers.NodeRolesId;
import org.batfish.identifiers.QuestionId;
import org.batfish.identifiers.QuestionSettingsId;
import org.batfish.identifiers.SnapshotId;
import org.batfish.role.NodeRolesData;
import org.javaswift.joss.model.StoredObject;

import net.jpountz.lz4.LZ4FrameInputStream;
import net.jpountz.lz4.LZ4FrameOutputStream;

/**
 * A utility class that abstracts the underlying file system storage used by
 * Batfish.
 */
@ParametersAreNonnullByDefault
public final class SwiftBasedStorage implements StorageProvider {

    private static final String RELPATH_NODE_ROLES_DIR = "node_roles";

    private SwiftClient client;

    private final BatfishLogger _logger;
    
    private final BiFunction<String, Integer, AtomicInteger> _newBatch;

    public SwiftBasedStorage(String container, BatfishLogger logger) {
        try {
            client = new SwiftClient(container);
        } catch (IOException e) {
            throw new BatfishException(e.getMessage());
        }
        
        _logger = logger;
        _newBatch = (a, b) -> new AtomicInteger();
    }

    private @Nonnull String getNetworkDir(NetworkId networkId) {
        return networkId.getId();
    }

    private @Nonnull String getSnapshotDir(NetworkId networkId, SnapshotId snapshotId) {
        return SwiftUtil.resolvePath(getNetworkDir(networkId), BfConsts.RELPATH_SNAPSHOTS_DIR, snapshotId.getId());
    }
    private @Nonnull String getSnapshotMetadataPath(NetworkId networkId, SnapshotId snapshotId) {
        return SwiftUtil.resolvePath(getSnapshotDir(networkId, snapshotId), BfConsts.RELPATH_OUTPUT, BfConsts.RELPATH_METADATA_FILE);
    }

    private @Nonnull String getVendorIndependentConfigDir(NetworkId network, SnapshotId snapshot) {
        return SwiftUtil.resolvePath(getSnapshotDir(network, snapshot), BfConsts.RELPATH_OUTPUT, BfConsts.RELPATH_VENDOR_INDEPENDENT_CONFIG_DIR);
    }

    private @Nonnull String getConvertAnswerPath(NetworkId network, SnapshotId snapshot) {
        return SwiftUtil.resolvePath(getSnapshotDir(network, snapshot), BfConsts.RELPATH_OUTPUT, BfConsts.RELPATH_CONVERT_ANSWER_PATH);
    }

    public @Nonnull String getAdHocQuestionsDir(NetworkId networkId) {
        return SwiftUtil.resolvePath(getNetworkDir(networkId), BfConsts.RELPATH_QUESTIONS_DIR);
    }
    public @Nonnull String getAdHocQuestionDir(NetworkId network, QuestionId question) {
        return SwiftUtil.resolvePath(getAdHocQuestionsDir(network), question.getId());
    }
    private @Nonnull String getQuestionDir(
        NetworkId network, QuestionId question, @Nullable AnalysisId analysis) {
        // TODO consider analysis
    //   return analysis != null
    //       ? getAnalysisQuestionDir(network, question, analysis)
        return getAdHocQuestionDir(network, question);
    }

    private @Nonnull String getQuestionPath(
        NetworkId network, QuestionId question, @Nullable AnalysisId analysis) {
      return SwiftUtil.resolvePath(getQuestionDir(network, question, analysis), BfConsts.RELPATH_QUESTION_FILE);
    }

    private @Nonnull String getAnswerDir(AnswerId answerId) {
        return SwiftUtil.resolvePath(BfConsts.RELPATH_ANSWERS_DIR, answerId.getId());
    }

    private @Nonnull String getAnswerPath(AnswerId answerId) {
        return SwiftUtil.resolvePath(getAnswerDir(answerId), BfConsts.RELPATH_ANSWER_JSON);
    }

    private @Nonnull String getAnswerMetadataPath(AnswerId answerId) {
        return SwiftUtil.resolvePath(getAnswerDir(answerId), BfConsts.RELPATH_ANSWER_METADATA);
    }

    private String getNodeRolesDir() {
        return RELPATH_NODE_ROLES_DIR;
    }

    private @Nonnull String getNodeRolesPath(NodeRolesId nodeRolesId) {
        return SwiftUtil.resolvePath(getNodeRolesDir(), String.format("%s%s", nodeRolesId.getId(), ".json"));
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
        } catch (Throwable e) {
            throw new BatfishException("Failed to serialize object to output file: " + outputPath, e);
        }
    }

    private <S extends Serializable> SortedMap<String, S> deserializeObjects(
        Map<String, String> namesByPath, Class<S> outputClass) {
        String outputClassName = outputClass.getName();
        AtomicInteger completed =
            _newBatch.apply(
                String.format("Deserializing objects of type '%s' from files", outputClassName),
                namesByPath.size());
        return new TreeMap<>(
            namesByPath
                .entrySet()
                .parallelStream()
                .collect(
                    Collectors.toMap(
                        Entry::getValue,
                        entry -> {
                            String inputPath = entry.getKey();
                            String name = entry.getValue();
                            _logger.debugf(
                                "Reading %s '%s' from '%s'\n", outputClassName, name, inputPath);
                            S output = deserializeObject(inputPath, outputClass);
                            completed.incrementAndGet();
                            return output;
                        }
                )
            ));
    }

    private <S extends Serializable> S deserializeObject(String inputPath, Class<S> outputClass) throws BatfishException {
        try (Closer closer = Closer.create()) {
            InputStream fis = closer.register(client.getInputStream(inputPath));
            PushbackInputStream pbstream = new PushbackInputStream(fis, DEFAULT_HEADER_LENGTH_BYTES);
            Format f = detectFormat(pbstream);
            ObjectInputStream ois;
            if (f == Format.GZIP) {
                GZIPInputStream gis =
                    closer.register(new GZIPInputStream(pbstream, 8192 /* enlarge buffer */));
                ois = new ObjectInputStream(gis);
            } else if (f == Format.LZ4) {
                LZ4FrameInputStream lis = closer.register(new LZ4FrameInputStream(pbstream));
                ois = new ObjectInputStream(lis);
            } else if (f == Format.JAVA_SERIALIZED) {
                ois = new ObjectInputStream(pbstream);
            } else {
                throw new BatfishException(
                    String.format("Could not detect format of the file %s", inputPath));
            }
            closer.register(ois);
            return outputClass.cast(ois.readObject());
        } catch (Exception e) {
            throw new BatfishException(
                String.format(
                    "Failed to deserialize object of type %s from file %s",
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
        } catch (BatfishException e) {
          return null;
        }
    }

    @Override
    public ConvertConfigurationAnswerElement loadConvertConfigurationAnswerElement(
            NetworkId network, SnapshotId snapshot) {
        String ccaePath = getConvertAnswerPath(network, snapshot);
        try {
            return deserializeObject(ccaePath, ConvertConfigurationAnswerElement.class);
        } catch (BatfishException e) {
            _logger.errorf(
                "Failed to deserialize ConvertConfigurationAnswerElement: %s",
                Throwables.getStackTraceAsString(e));
            return null;
        }
        
    }

    @Override
    public SortedSet<NodeInterfacePair> loadInterfaceBlacklist(NetworkId network,
            SnapshotId snapshot) {
        return null;
    }

    @Override
    public IspConfiguration loadIspConfiguration(NetworkId network, SnapshotId snapshot) {
        return null;
    }

    @Override
    public SortedSet<String> loadNodeBlacklist(NetworkId network, SnapshotId snapshot) {
        return null;
    }

    @Override
    public Layer1Topology loadLayer1Topology(NetworkId network, SnapshotId snapshot) {
        return null;
    }

    @Override
    public MajorIssueConfig loadMajorIssueConfig(NetworkId network,
            IssueSettingsId majorIssueType) {
        return null;
    }

    @Override
    public String loadWorkLog(NetworkId network, SnapshotId snapshot, String workId)
            throws IOException {
        return null;
    }

    @Override
    public void storeMajorIssueConfig(NetworkId network, IssueSettingsId majorIssueType,
            MajorIssueConfig majorIssueConfig) throws IOException {

    }

    @Override
    public void storeConfigurations(Map<String, Configuration> configurations,
            ConvertConfigurationAnswerElement convertAnswerElement, NetworkId network,
            SnapshotId snapshot) {
        String ccaePath = getConvertAnswerPath(network, snapshot);
        serializeObject(convertAnswerElement, ccaePath);

        String batchName =
            String.format(
                "Serializing %s vendor-independent configuration structures for snapshot %s",
                    configurations.size(), snapshot);
        _logger.infof("\n*** %s***\n", batchName.toUpperCase());
        AtomicInteger progressCount = _newBatch.apply(batchName, configurations.size());
        
        String outputDir = getVendorIndependentConfigDir(network, snapshot);
        configurations
            .entrySet()
            .parallelStream()
            .forEach(
                e -> {
                    String currentOutputPath = SwiftUtil.resolvePath(outputDir, e.getKey());
                    serializeObject(e.getValue(), currentOutputPath);
                    progressCount.incrementAndGet();
                });
    }
    

    @Override
    public void storeAnswer(String answerStr, AnswerId answerId) {
        String answerPath = getAnswerPath(answerId);
        client.setString(answerPath, answerStr);
    }

    @Override
    public void storeAnswerMetadata(AnswerMetadata answerMetadata, AnswerId answerId) {
        String metricsStr;
        try {
          metricsStr = BatfishObjectMapper.writeString(answerMetadata);
        } catch (JsonProcessingException e) {
          throw new BatfishException("Could not write answer metrics", e);
        }
        client.setString(getAnswerMetadataPath(answerId), metricsStr);
    }

    @Override
    public String loadQuestion(NetworkId network, QuestionId question, AnalysisId analysis) {
        try {
            return client.getString(getQuestionPath(network, question, analysis));
        } catch (IOException e) {
            throw new BatfishException(e.getMessage());
        }
    }

    @Override
    public boolean checkQuestionExists(NetworkId network, QuestionId question,
            AnalysisId analysis) {
        Collection<StoredObject> l = client.list(getQuestionPath(network, question, analysis));
        return l.stream().filter(x -> x.getName().contains(question.getId())).count() > 0;
    }

    public boolean hasAnswer(AnswerId answerId) {
        return client.exists(getAnswerPath(answerId));
    }

    @Override
    public String loadAnswer(AnswerId answerId) throws FileNotFoundException, IOException {
        if (!hasAnswer(answerId)) {
          throw new IOException(String.format("Could not find answer with ID: %s", answerId));
        }
        try {
            return client.getString(getAnswerPath(answerId));
        } catch (IOException e) {
            throw new BatfishException(e.getMessage());
        }
    }

    @Override
    public AnswerMetadata loadAnswerMetadata(AnswerId answerId)
            throws FileNotFoundException, IOException {
        if (!hasAnswerMetadata(answerId)) {
            throw new IOException(
                String.format("Could not find answer metadata for ID: %s", answerId));
        }
        try {
            String answerMetadataStr = client.getString(getAnswerMetadataPath(answerId));
            return BatfishObjectMapper.mapper()
                        .readValue(answerMetadataStr, new TypeReference<AnswerMetadata>() {});
        } catch (IOException e) {
            throw new BatfishException(e.getMessage());
        }
        
    }

    @Override
    public boolean hasAnswerMetadata(AnswerId answerId) {
        return client.exists(getAnswerMetadataPath(answerId));
    }

    @Override
    public void storeQuestion(String questionStr, NetworkId network, QuestionId question,
            AnalysisId analysis) {
        String questionPath = getQuestionPath(network, question, analysis);
        client.setString(questionPath, questionStr);
    }

    @Override
    public String loadQuestionSettings(NetworkId networkId, QuestionSettingsId questionSettingsId)
            throws IOException {
        return null;
    }

    @Override
    public boolean checkNetworkExists(NetworkId network) {
        return client.exists(network.getId());
    }

    @Override
    public void storeQuestionSettings(String settings, NetworkId networkId,
            QuestionSettingsId questionSettingsId) throws IOException {

    }

    @Override
    public String loadQuestionClassId(NetworkId networkId, QuestionId questionId,
            AnalysisId analysisId) throws FileNotFoundException, IOException {
        return Question.parseQuestion(loadQuestion(networkId, questionId, analysisId)).getName();
    }

    @Override
    public boolean hasAnalysisMetadata(NetworkId networkId, AnalysisId analysisId) {
        return false;
    }

    @Override
    public void storeAnalysisMetadata(AnalysisMetadata analysisMetadata, NetworkId networkId,
            AnalysisId analysisId) throws IOException {

    }

    @Override
    public String loadAnalysisMetadata(NetworkId networkId, AnalysisId analysisId)
            throws FileNotFoundException, IOException {
        return null;
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
    public void storeNodeRoles(NodeRolesData nodeRolesData, NodeRolesId nodeRolesId)
            throws IOException {
        client.setString(getNodeRolesPath(nodeRolesId), BatfishObjectMapper.writeString(nodeRolesData));
    }

    @Override
    public String loadNodeRoles(NodeRolesId nodeRolesId) throws FileNotFoundException, IOException {
        return client.getString(getNodeRolesPath(nodeRolesId));
    }

    @Override
    public boolean hasNodeRoles(NodeRolesId nodeRolesId) {
        return client.exists(getNodeRolesPath(nodeRolesId));
    }

    @Override
    public void initNetwork(NetworkId networkId) {
        try {
            client.mkdir(networkId.getId());
        } catch (Exception e) {
            throw new BatfishException(e.getMessage());
        }
    }

    @Override
    public void deleteAnswerMetadata(AnswerId answerId) throws FileNotFoundException, IOException {

    }

    @Override
    public InputStream loadNetworkObject(NetworkId networkId, String key)
            throws FileNotFoundException, IOException {
        return null;
    }

    @Override
    public void storeNetworkObject(InputStream inputStream, NetworkId networkId, String key)
            throws IOException {

    }

    @Override
    public void deleteNetworkObject(NetworkId networkId, String key)
            throws FileNotFoundException, IOException {

    }

    @Override
    public InputStream loadNetworkBlob(NetworkId networkId, String key)
            throws FileNotFoundException, IOException {
        return null;
    }

    @Override
    public void storeNetworkBlob(InputStream inputStream, NetworkId networkId, String key)
            throws IOException {

    }

    @Override
    public InputStream loadSnapshotObject(NetworkId networkId, SnapshotId snapshotId, String key)
            throws FileNotFoundException, IOException {
        return null;
    }

    @Override
    public void storeSnapshotObject(InputStream inputStream, NetworkId networkId,
            SnapshotId snapshotId, String key) throws IOException {

    }

    @Override
    public void deleteSnapshotObject(NetworkId networkId, SnapshotId snapshotId, String key)
            throws FileNotFoundException, IOException {

    }

    @Override
    public InputStream loadSnapshotInputObject(NetworkId networkId, SnapshotId snapshotId,
            String key) throws FileNotFoundException, IOException {
        return null;
    }

    @Override
    public List<StoredObjectMetadata> getSnapshotInputObjectsMetadata(NetworkId networkId,
            SnapshotId snapshotId) throws IOException {
        return null;
    }

    @Override
    public List<StoredObjectMetadata> getSnapshotExtendedObjectsMetadata(NetworkId networkId,
            SnapshotId snapshotId) throws IOException {
        return null;
    }

    @Override
    public String loadPojoTopology(NetworkId networkId, SnapshotId snapshotId) throws IOException {
        return null;
    }

    @Override
    public String loadInitialTopology(NetworkId networkId, SnapshotId snapshotId)
            throws IOException {
        return null;
    }

    @Override
    public void storeInitialTopology(Topology topology, NetworkId networkId, SnapshotId snapshotId)
            throws IOException {

    }

    @Override
    public void storePojoTopology(org.batfish.datamodel.pojo.Topology topology, NetworkId networkId,
            SnapshotId snapshotId) throws IOException {

    }

    @Override
    public void storeWorkLog(String logOutput, NetworkId network, SnapshotId snapshot,
            String workId) throws IOException {

    }

    @Override
    public CompletionMetadata loadCompletionMetadata(NetworkId networkId, SnapshotId snapshotId)
            throws IOException {
        return null;
    }

    @Override
    public void storeCompletionMetadata(CompletionMetadata completionMetadata, NetworkId networkId,
            SnapshotId snapshotId) throws IOException {

    }

    @Override
    public BgpTopology loadBgpTopology(NetworkSnapshot networkSnapshot) throws IOException {
        return null;
    }

    @Override
    public EigrpTopology loadEigrpTopology(NetworkSnapshot networkSnapshot) throws IOException {
        return null;
    }

    @Override
    public Optional<Layer2Topology> loadLayer2Topology(NetworkSnapshot networkSnapshot)
            throws IOException {
        return null;
    }

    @Override
    public Topology loadLayer3Topology(NetworkSnapshot networkSnapshot) throws IOException {
        return null;
    }

    @Override
    public OspfTopology loadOspfTopology(NetworkSnapshot networkSnapshot) throws IOException {
        return null;
    }

    @Override
    public VxlanTopology loadVxlanTopology(NetworkSnapshot networkSnapshot) throws IOException {
        return null;
    }

  @Override
  public void storeBgpTopology(BgpTopology bgpTopology, NetworkSnapshot networkSnapshot)
      throws IOException {
    
  }

  @Override
  public void storeEigrpTopology(EigrpTopology eigrpTopology, NetworkSnapshot networkSnapshot)
      throws IOException {
    
  }

  @Override
  public void storeLayer2Topology(Optional<Layer2Topology> layer2Topology,
      NetworkSnapshot networkSnapshot) throws IOException {
    
  }

  @Override
  public void storeLayer3Topology(Topology layer3Topology, NetworkSnapshot networkSnapshot)
      throws IOException {
    
  }

  @Override
  public void storeOspfTopology(OspfTopology ospfTopology, NetworkSnapshot networkSnapshot)
      throws IOException {
    
  }

  @Override
  public void storeVxlanTopology(VxlanTopology vxlanTopology, NetworkSnapshot networkSnapshot)
      throws IOException {
    
  }
}