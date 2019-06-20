package org.batfish.bddreachability;

import com.google.common.collect.Streams;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.stream.Stream;
import javax.annotation.Nonnull;
import net.sf.javabdd.BDD;
import org.batfish.symbolic.state.NodeAccept;
import org.batfish.symbolic.state.NodeDropNoRoute;
import org.batfish.symbolic.state.NodeDropNullRoute;
import org.batfish.symbolic.state.StateExpr;

public class BDDForwardingGenerator {
  /*
   * node --> vrf --> interface --> set of packets that get routed out the interface but do not
   * reach the neighbor, or exits network, or delivered to subnet
   * This includes neighbor unreachable, exits network, and delivered to subnet
   */
  private final Map<String, Map<String, Map<String, BDD>>> _neighborUnreachableBDDs;

  private final Map<String, Map<String, Map<String, BDD>>> _deliveredToSubnetBDDs;

  private final Map<String, Map<String, Map<String, BDD>>> _exitsNetworkBDDs;

  private final Map<String, Map<String, Map<String, BDD>>> _insufficientInfoBDDs;

  // node --> vrf --> set of packets accepted by the vrf
  private final Map<String, Map<String, BDD>> _vrfAcceptBDDs;

  // node --> vrf --> set of packets routable by the vrf
  private final Map<String, Map<String, BDD>> _routableBDDs;

  // node --> vrf --> nextVrf --> set of packets vrf delegates to nextVrf
  private final Map<String, Map<String, Map<String, BDD>>> _nextVrfBDDs;

  // node --> vrf --> set of packets null-routed by the vrf
  private final Map<String, Map<String, BDD>> _nullRoutedBDDs;

  BDDForwardingGenerator() {}

  public Stream<Edge> generateForwardingEdges(
      BiFunction<String, String, StateExpr> postInVrf,
      BiFunction<String, String, StateExpr> preOutVrf,
      BiFunction<String, String, StateExpr> preOutInterfaceDeliveredToSubnet,
      BiFunction<String, String, StateExpr> preOutInterfaceExitsNetwork,
      BiFunction<String, String, StateExpr> preOutInterfaceInsufficientInfo) {
    return Streams.concat(
        generateRules_PostInVrf_NodeAccept(postInVrf),
        generateRules_PostInVrf_NodeDropNoRoute(postInVrf),
        generateRules_PostInVrf_PostInVrf(postInVrf),
        generateRules_PreOutVrf_PreOutInterfaceDisposition(
            preOutVrf,
            preOutInterfaceDeliveredToSubnet,
            preOutInterfaceExitsNetwork,
            preOutInterfaceInsufficientInfo,
            preOutInterfaceDeliveredToSubnet));
  }

  private Stream<Edge> generateRules_PostInVrf_NodeAccept(
      BiFunction<String, String, StateExpr> postInVrf) {
    return _vrfAcceptBDDs.entrySet().stream()
        .flatMap(
            nodeEntry ->
                nodeEntry.getValue().entrySet().stream()
                    .map(
                        vrfEntry -> {
                          String node = nodeEntry.getKey();
                          String vrf = vrfEntry.getKey();
                          BDD acceptBDD = vrfEntry.getValue();
                          return new Edge(
                              postInVrf.apply(node, vrf), new NodeAccept(node), acceptBDD);
                        }));
  }

  private Stream<Edge> generateRules_PostInVrf_NodeDropNoRoute(
      BiFunction<String, String, StateExpr> postInVrf) {
    return _vrfAcceptBDDs.entrySet().stream()
        .flatMap(
            nodeEntry ->
                nodeEntry.getValue().entrySet().stream()
                    .map(
                        vrfEntry -> {
                          String node = nodeEntry.getKey();
                          String vrf = vrfEntry.getKey();
                          BDD acceptBDD = vrfEntry.getValue();
                          BDD routableBDD = _nullRoutedBDDs.get(node).get(vrf);
                          return new Edge(
                              postInVrf.apply(node, vrf),
                              new NodeDropNoRoute(node),
                              acceptBDD.nor(routableBDD));
                        }));
  }

  private Stream<Edge> generateRules_PreOutVrf_NodeDropNullRoute(
      BiFunction<String, String, StateExpr> preOutVrf) {
    return _nullRoutedBDDs.entrySet().stream()
        .flatMap(
            nodeEntry ->
                nodeEntry.getValue().entrySet().stream()
                    .map(
                        vrfEntry -> {
                          String node = nodeEntry.getKey();
                          String vrf = vrfEntry.getKey();
                          BDD nullRoutedBDD = vrfEntry.getValue();
                          return new Edge(
                              preOutVrf.apply(node, vrf),
                              new NodeDropNullRoute(node),
                              nullRoutedBDD);
                        }));
  }

  /** Generate edges from vrf to nextVrf */
  private Stream<Edge> generateRules_PostInVrf_PostInVrf(
      BiFunction<String, String, StateExpr> postInVrf) {
    return _nextVrfBDDs.entrySet().stream()
        .flatMap(
            nodeEntry -> {
              String node = nodeEntry.getKey();
              return nodeEntry.getValue().entrySet().stream()
                  .flatMap(
                      vrfEntry -> {
                        String vrf = vrfEntry.getKey();
                        return vrfEntry.getValue().entrySet().stream()
                            .map(
                                nextVrfEntry -> {
                                  String nextVrf = nextVrfEntry.getKey();
                                  BDD nextVrfBDD = nextVrfEntry.getValue();
                                  return new Edge(
                                      postInVrf.apply(node, vrf),
                                      postInVrf.apply(node, nextVrf),
                                      nextVrfBDD);
                                });
                      });
            });
  }

  private Stream<Edge> generateRules_PreOutVrf_PreOutInterfaceDisposition(
      BiFunction<String, String, StateExpr> preOutVrf,
      BiFunction<String, String, StateExpr> preOutInterfaceDeliveredToSubnet,
      BiFunction<String, String, StateExpr> preOutInterfaceExitsNetwork,
      BiFunction<String, String, StateExpr> preOutInterfaceInsufficientInfo,
      BiFunction<String, String, StateExpr> preOutInterfaceNeighborUnreachable) {
    return Streams.concat(
        generateRules_PreOutVrf_PreOutInterfaceDisposition(
            _deliveredToSubnetBDDs, preOutVrf, preOutInterfaceDeliveredToSubnet),
        generateRules_PreOutVrf_PreOutInterfaceDisposition(
            _exitsNetworkBDDs, preOutVrf, preOutInterfaceExitsNetwork),
        generateRules_PreOutVrf_PreOutInterfaceDisposition(
            _insufficientInfoBDDs, preOutVrf, preOutInterfaceInsufficientInfo),
        generateRules_PreOutVrf_PreOutInterfaceDisposition(
            _neighborUnreachableBDDs, preOutVrf, preOutInterfaceNeighborUnreachable));
  }

  @Nonnull
  private static Stream<Edge> generateRules_PreOutVrf_PreOutInterfaceDisposition(
      Map<String, Map<String, Map<String, BDD>>> dispositionBddMap,
      BiFunction<String, String, StateExpr> preOutVrfConstructor,
      BiFunction<String, String, StateExpr> preOutInterfaceDispositionConstructor) {
    return dispositionBddMap.entrySet().stream()
        .flatMap(
            nodeEntry -> {
              String hostname = nodeEntry.getKey();
              return nodeEntry.getValue().entrySet().stream()
                  .flatMap(
                      vrfEntry -> {
                        String vrfName = vrfEntry.getKey();
                        StateExpr preState = preOutVrfConstructor.apply(hostname, vrfName);
                        return vrfEntry.getValue().entrySet().stream()
                            .filter(e -> !e.getValue().isZero())
                            .map(
                                ifaceEntry -> {
                                  String ifaceName = ifaceEntry.getKey();
                                  BDD bdd = ifaceEntry.getValue();
                                  return new Edge(
                                      preState,
                                      preOutInterfaceDispositionConstructor.apply(
                                          hostname, ifaceName),
                                      bdd);
                                });
                      });
            });
  }
}
