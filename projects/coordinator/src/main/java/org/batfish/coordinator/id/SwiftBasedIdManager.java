package org.batfish.coordinator.id;

import java.io.IOException;
import java.util.UUID;

import javax.annotation.Nonnull;

import org.batfish.common.BatfishException;
import org.batfish.identifiers.AnalysisId;
import org.batfish.identifiers.IssueSettingsId;
import org.batfish.identifiers.NetworkId;
import org.batfish.identifiers.NodeRolesId;
import org.batfish.identifiers.QuestionId;
import org.batfish.identifiers.QuestionSettingsId;
import org.batfish.identifiers.SnapshotId;
import org.batfish.identifiers.SwiftBasedIdResolver;

public class SwiftBasedIdManager extends SwiftBasedIdResolver implements IdManager {

    public SwiftBasedIdManager(String container) {
        super(container);
    }

    private static @Nonnull String uuid() {
        return UUID.randomUUID().toString();
    }

    @Override
    public void assignAnalysis(String analysis, NetworkId networkId, AnalysisId analysisId) {

    }

    @Override
    public void assignIssueSettingsId(String majorIssueType, NetworkId networkId,
            IssueSettingsId issueSettingsId) {

    }

    @Override
    public void assignNetwork(String network, NetworkId networkId) {
        String path = getNetworkIdPath(network);
        client.setString(path, networkId.getId());
    }

    @Override
    public void assignNetworkNodeRolesId(NetworkId networkId, NodeRolesId networkNodeRolesId) {

    }

    @Override
    public void assignQuestion(String question, NetworkId networkId, QuestionId questionId,
            AnalysisId analysisId) {
        String idFile = getQuestionIdPath(question, networkId, analysisId);
        client.setString(idFile, questionId.getId());
    }

    @Override
    public void assignQuestionSettingsId(String questionClassId, NetworkId networkId,
            QuestionSettingsId questionSettingsId) {

    }

    @Override
    public void assignSnapshot(String snapshot, NetworkId networkId, SnapshotId snapshotId) {
        String idFile = getSnapshotIdPath(snapshot, networkId);
        client.setString(idFile, snapshotId.getId());
    }

    @Override
    public void deleteAnalysis(String analysis, NetworkId networkId) {

    }

    @Override
    public void deleteNetwork(String network) {

    }

    @Override
    public void deleteQuestion(String question, NetworkId networkId, AnalysisId analysisId) {

    }

    @Override
    public void deleteSnapshot(String snapshot, NetworkId networkId) {
        client.delete(getSnapshotIdPath(snapshot, networkId));
    }

    @Override
    public AnalysisId generateAnalysisId() {
        return null;
    }

    @Override
    public IssueSettingsId generateIssueSettingsId() {
        return null;
    }

    @Override
    public NetworkId generateNetworkId() {
        return new NetworkId(uuid());
    }

    @Override
    public NodeRolesId generateNetworkNodeRolesId() {
        return null;
    }

    @Override
    public QuestionId generateQuestionId() {
        return new QuestionId(uuid());
    }

    @Override
    public QuestionSettingsId generateQuestionSettingsId() {
    return null;
  }

  @Override
  public SnapshotId generateSnapshotId() {
    return new SnapshotId(uuid());
  }
    
}