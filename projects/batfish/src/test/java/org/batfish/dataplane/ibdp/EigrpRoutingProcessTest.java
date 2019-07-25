package org.batfish.dataplane.ibdp;

import static org.batfish.datamodel.Configuration.DEFAULT_VRF_NAME;

import com.google.common.collect.ImmutableMap;
import com.google.common.graph.MutableNetwork;
import com.google.common.graph.NetworkBuilder;
import java.util.Map;
import org.batfish.datamodel.Configuration;
import org.batfish.datamodel.ConfigurationFormat;
import org.batfish.datamodel.EigrpInternalRoute;
import org.batfish.datamodel.Ip;
import org.batfish.datamodel.NetworkFactory;
import org.batfish.datamodel.eigrp.EigrpEdge;
import org.batfish.datamodel.eigrp.EigrpNeighborConfigId;
import org.batfish.datamodel.eigrp.EigrpProcess;
import org.batfish.datamodel.eigrp.EigrpProcessMode;
import org.batfish.datamodel.eigrp.EigrpTopology;
import org.junit.Test;

/** Tests of {@link EigrpRoutingProcess} */
public class EigrpRoutingProcessTest {
  @Test
  public void testUpdateTopology() {
    Configuration c1 =
        new NetworkFactory()
            .configurationBuilder()
            .setConfigurationFormat(ConfigurationFormat.CISCO_IOS)
            .setHostname("n1")
            .build();
    Configuration c2 =
        new NetworkFactory()
            .configurationBuilder()
            .setConfigurationFormat(ConfigurationFormat.CISCO_IOS)
            .setHostname("n1")
            .build();

    EigrpProcess proc1 =
        EigrpProcess.builder()
            .setAsNumber(1)
            .setMode(EigrpProcessMode.CLASSIC)
            .setRouterId(Ip.ZERO)
            .build();
    EigrpProcess proc2 =
        EigrpProcess.builder()
            .setAsNumber(1)
            .setMode(EigrpProcessMode.CLASSIC)
            .setRouterId(Ip.ZERO)
            .build();

    EigrpRoutingProcess routingProc1 = new EigrpRoutingProcess(proc1, DEFAULT_VRF_NAME, c1);
    EigrpRoutingProcess routingProc2 = new EigrpRoutingProcess(proc2, DEFAULT_VRF_NAME, c2);

    // merge some routes in
    EigrpInternalRoute ri = EigrpInternalRoute.builder().build();
    routingProc1._internalRib.mergeRoute(ri);
    EigrpInternalRoute re = EigrpInternalRoute.builder().build();
    routingProc1._internalRib.mergeRoute(ri);

    MutableNetwork<EigrpNeighborConfigId, EigrpEdge> mutableNetwork =
        NetworkBuilder.directed().build();
    EigrpNeighborConfigId ec1 =
        new EigrpNeighborConfigId(1, c1.getHostname(), "i1", DEFAULT_VRF_NAME);
    EigrpNeighborConfigId ec2 =
        new EigrpNeighborConfigId(1, c1.getHostname(), "i2", DEFAULT_VRF_NAME);
    EigrpEdge edge = new EigrpEdge(ec1, ec2);

    mutableNetwork.addEdge(ec1, ec2, edge);
    mutableNetwork.addEdge(ec2, ec1, edge.reverse());
    EigrpTopology topology = new EigrpTopology(mutableNetwork);
    routingProc1.updateTopology(topology);
    routingProc2.updateTopology(topology);

    Map<String, Node> nodes =
        ImmutableMap.of(c1.getHostname(), new Node(c1), c2.getHostname(), new Node(c2));
    // Test
    routingProc1.executeIteration(nodes);

    // TODO: finish this
  }
}
