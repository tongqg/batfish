package org.batfish.identifiers;

import static java.util.Optional.ofNullable;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Comparator;
import java.util.Set;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.ebay.fwauto.excavator.openstack.SwiftClient;
import com.ebay.fwauto.excavator.openstack.SwiftUtil;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSortedSet;
import com.google.common.hash.Hashing;

import org.batfish.common.BatfishException;
import org.javaswift.joss.model.StoredObject;

public class SwiftBasedIdResolver implements IdResolver {

    private static final String ID_EXTENSION = ".id";

    private static final String RELPATH_ANALYSIS_IDS = "analysis_ids";
  
    private static final String RELPATH_ISSUE_SETTINGS_IDS = "analysis_ids";
  
    private static final String RELPATH_NETWORK_IDS = "network_ids";
  
    private static final String RELPATH_NETWORK_NODE_ROLES_ID = "network_node_roles.id";
  
    private static final String RELPATH_QUESTION_IDS = "question_ids";
  
    private static final String RELPATH_QUESTION_SETTINGS_IDS = "question_settings_ids";
  
    private static final String RELPATH_SNAPSHOT_IDS = "snapshot_ids";

    protected SwiftClient client;
    public SwiftBasedIdResolver(String container) {
        try {
            client = new SwiftClient(container);
        } catch (IOException e) {
            throw new BatfishException(e.getMessage());
        }
        
    }

    private static @Nonnull String hash(String input) {
        return Hashing.murmur3_128().hashString(input, StandardCharsets.UTF_8).toString();
    }

    protected @Nonnull String getNetworkIdsDir() {
        return RELPATH_NETWORK_IDS;
    }

    protected @Nonnull String getNetworkIdPath(String network) {
        return SwiftUtil.resolvePath(getNetworkIdsDir(), String.format("%s%s", network, ID_EXTENSION));
    }

    protected @Nonnull String getNetworkDir(NetworkId networkId) {
        return networkId.getId();
    }
    protected @Nonnull String getSnapshotIdsDir(NetworkId networkId) {
        return SwiftUtil.resolvePath(getNetworkDir(networkId), RELPATH_SNAPSHOT_IDS);
    }

    protected @Nonnull String getSnapshotIdPath(String snapshot, NetworkId networkId) {
        return SwiftUtil.resolvePath(getSnapshotIdsDir(networkId), String.format("%s%s", snapshot, ID_EXTENSION));
    }

    protected @Nonnull String getQuestionIdsDir(NetworkId networkId, @Nullable AnalysisId analysisId) {
        // TODO handle analysis dir
       return SwiftUtil.resolvePath(getNetworkDir(networkId), RELPATH_QUESTION_IDS);
    }

    protected @Nonnull String getQuestionIdPath(
        String question, NetworkId networkId, @Nullable AnalysisId analysisId) {
      return SwiftUtil.resolvePath(getQuestionIdsDir(networkId, analysisId), String.format("%s%s", question, ID_EXTENSION));
    }

    protected @Nonnull String getQuestionSettingsIdsDir(NetworkId networkId) {
        return SwiftUtil.resolvePath(getNetworkDir(networkId), RELPATH_QUESTION_SETTINGS_IDS);
    }

    protected @Nonnull String getQuestionSettingsIdPath(String questionClassId, NetworkId networkId) {
        return SwiftUtil.resolvePath(getQuestionSettingsIdsDir(networkId), String.format("%s%s", questionClassId, ID_EXTENSION));
    }

    protected @Nonnull String getNetworkNodeRolesIdPath(NetworkId networkId) {
        return SwiftUtil.resolvePath(getNetworkDir(networkId), RELPATH_NETWORK_NODE_ROLES_ID);
    }

    @Override
    public AnalysisId getAnalysisId(String analysis, NetworkId networkId) {
        return null;
    }

    @Override
    public AnswerId getBaseAnswerId(NetworkId networkId, SnapshotId snapshotId,
            QuestionId questionId, QuestionSettingsId questionSettingsId,
            NodeRolesId networkNodeRolesId, SnapshotId referenceSnapshotId, AnalysisId analysisId) {
        return new AnswerId(
            hash(
                ImmutableList.of(
                        networkId,
                        snapshotId,
                        questionId,
                        questionSettingsId,
                        networkNodeRolesId,
                        ofNullable(referenceSnapshotId),
                        ofNullable(analysisId))
                    .toString()));
    }

    @Override
    public AnswerId getFinalAnswerId(AnswerId baseAnswerId, Set<IssueSettingsId> issueSettingsIds) {
        return new AnswerId(
            hash(
                ImmutableList.of(
                        baseAnswerId,
                        ImmutableSortedSet.copyOf(
                            Comparator.comparing(IssueSettingsId::getId), issueSettingsIds))
                    .toString()));
    }

    @Override
    public IssueSettingsId getIssueSettingsId(String majorIssueType, NetworkId networkId) {
        return null;
    }

    @Override
    public NetworkId getNetworkId(String network) {
        if (!hasNetworkId(network)) {
            throw new IllegalArgumentException(String.format("No ID assigned to non-existent network %s", network));
        }
        try {
            return new NetworkId(client.getString(getNetworkIdPath(network)));
        } catch (IOException e) {
            throw new BatfishException(e.getMessage());
        }
    }

    @Override
    public NodeRolesId getNetworkNodeRolesId(NetworkId networkId) {
        if (!hasNetworkNodeRolesId(networkId)) {
            throw new IllegalArgumentException("No assigned node-roles ID");
        }
        try {
            return new NodeRolesId(client.getString(getNetworkNodeRolesIdPath(networkId)));
        } catch (IOException e) {
            throw new BatfishException(e.getMessage());
        }
        
    }

    @Override
    public QuestionId getQuestionId(String question, NetworkId networkId, AnalysisId analysisId) {
        if (!hasQuestionId(question, networkId, analysisId)) {
            throw new IllegalArgumentException(
                String.format("No ID assigned to non-existent question '%s'", question));
        }
        try {
            return new QuestionId(client.getString(getQuestionIdPath(question, networkId, analysisId)));
        } catch (IOException e) {
            throw new BatfishException(e.getMessage());
        }
    }

    @Override
    public QuestionSettingsId getQuestionSettingsId(String questionClassId, NetworkId networkId) {
        if (!hasQuestionSettingsId(questionClassId, networkId)) {
            throw new IllegalArgumentException(
                String.format("No ID assigned to non-configured questionClassId '%s'", questionClassId));
        }
        try {
            return new QuestionSettingsId(client.getString(getQuestionSettingsIdPath(questionClassId, networkId)));
        } catch (IOException e) {
            throw new BatfishException(e.getMessage());
        }
    }

    @Override
    public SnapshotId getSnapshotId(String snapshot, NetworkId networkId) {
        if (!hasSnapshotId(snapshot, networkId)) {
            throw new IllegalArgumentException(
                String.format("No ID assigned to non-existent snapshot '%s'", snapshot));
        }
        try {
            return new SnapshotId(client.getString(getSnapshotIdPath(snapshot, networkId)));
        } catch (IOException e) {
            throw new BatfishException(e.getMessage());
        }
    }

    @Override
    public NodeRolesId getSnapshotNodeRolesId(NetworkId networkId, SnapshotId snapshotId) {
        return new NodeRolesId(hash(ImmutableList.of(networkId, snapshotId).toString()));
    }

    @Override
    public boolean hasAnalysisId(String analysis, NetworkId networkId) {
        return false;
    }

    @Override
    public boolean hasIssueSettingsId(String majorIssueType, NetworkId networkId) {
        return false;
    }



    @Override
    public boolean hasNetworkId(String network) {
        return client.exists(getNetworkIdPath(network));
    }

    @Override
    public boolean hasNetworkNodeRolesId(NetworkId networkId) {
        return client.exists(getNetworkNodeRolesIdPath(networkId));
    }

    @Override
    public boolean hasQuestionId(String question, NetworkId networkId, AnalysisId analysisId) {
        return client.exists(getQuestionIdPath(question, networkId, analysisId));
    }

    @Override
    public boolean hasQuestionSettingsId(String questionClassId, NetworkId networkId) {
        return client.exists(getQuestionSettingsIdPath(questionClassId, networkId));
    }

    @Override
    public boolean hasSnapshotId(String snapshot, NetworkId networkId) {
        return client.exists(getSnapshotIdPath(snapshot, networkId));
    }

    @Override
    public Set<String> listAnalyses(NetworkId networkId) {
        return null;
    }

    @Override
    public Set<String> listNetworks() {
        return listResolvableNames(getNetworkIdsDir());
    }

    @Override
    public Set<String> listQuestions(NetworkId networkId, AnalysisId analysisId) {
        return null;
    }

    private @Nonnull Set<String> listResolvableNames(String idsDir) {
        Collection<StoredObject> l = client.list(idsDir);
        return l.stream().filter(o -> o.getName().endsWith(ID_EXTENSION))
            .map(StoredObject::getName).map(name -> name.substring(
                name.lastIndexOf("/")+1, name.length() - ID_EXTENSION.length()))
                    .collect(ImmutableSet.toImmutableSet());
    }
    @Override
    public Set<String> listSnapshots(NetworkId networkId) {
        return listResolvableNames(getSnapshotIdsDir(networkId));
    }
    
}