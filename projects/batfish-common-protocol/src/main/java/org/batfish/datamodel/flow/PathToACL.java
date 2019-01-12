package org.batfish.datamodel.flow;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;
import static org.batfish.datamodel.FlowDisposition.DELIVERED_TO_SUBNET;
import static org.batfish.datamodel.FlowDisposition.EXITS_NETWORK;
import static org.batfish.datamodel.FlowDisposition.INSUFFICIENT_INFO;
import static org.batfish.datamodel.FlowDisposition.NEIGHBOR_UNREACHABLE;
import static org.batfish.datamodel.LineAction.DENY;
import static org.batfish.datamodel.LineAction.PERMIT;
import static org.batfish.datamodel.acl.AclLineMatchExprs.matchDst;
import static org.batfish.datamodel.acl.AclLineMatchExprs.not;
import static org.batfish.datamodel.acl.AclLineMatchExprs.permittedByAcl;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSortedSet;
import com.google.common.collect.Lists;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import org.batfish.common.bdd.BDDPacket;
import org.batfish.common.bdd.BDDSourceManager;
import org.batfish.common.topology.TopologyUtil;
import org.batfish.datamodel.AclIpSpace;
import org.batfish.datamodel.Configuration;
import org.batfish.datamodel.EmptyIpSpace;
import org.batfish.datamodel.FlowDisposition;
import org.batfish.datamodel.ForwardingAnalysis;
import org.batfish.datamodel.IpAccessList;
import org.batfish.datamodel.IpAccessListLine;
import org.batfish.datamodel.IpIpSpace;
import org.batfish.datamodel.IpSpace;
import org.batfish.datamodel.IpSpaceReference;
import org.batfish.datamodel.IpWildcardIpSpace;
import org.batfish.datamodel.IpWildcardSetIpSpace;
import org.batfish.datamodel.LineAction;
import org.batfish.datamodel.Prefix;
import org.batfish.datamodel.PrefixIpSpace;
import org.batfish.datamodel.UniverseIpSpace;
import org.batfish.datamodel.acl.AclExplainer;
import org.batfish.datamodel.acl.AclLineMatchExpr;
import org.batfish.datamodel.acl.AclLineMatchExprLiterals;
import org.batfish.datamodel.acl.AclLineMatchExprs;
import org.batfish.datamodel.acl.AndMatchExpr;
import org.batfish.datamodel.acl.IpAccessListRenamer;
import org.batfish.datamodel.acl.MatchSrcInterface;
import org.batfish.datamodel.acl.PermittedByAcl;
import org.batfish.datamodel.collections.NodeInterfacePair;
import org.batfish.datamodel.flow.EnterInputIfaceStep.EnterInputIfaceStepDetail;
import org.batfish.datamodel.flow.FilterStep.FilterStepDetail;
import org.batfish.datamodel.flow.OriginateStep.OriginateStepDetail;
import org.batfish.datamodel.flow.RoutingStep.RoutingStepDetail;
import org.batfish.datamodel.visitors.GenericIpSpaceVisitor;
import org.batfish.datamodel.visitors.IpSpaceRenamer;

/**
 * Convert a path through the network to an {@link IpAccessList} that permits the headerspace that
 * can flow along that path.
 */
public class PathToACL {
  /** All the acls in the network, with globally-unique names */
  private final Map<String, IpAccessList> _acls;

  private final Map<String, Configuration> _configs;

  private final ForwardingAnalysis _forwardingAnalysis;

  private final Map<String, IpSpace> _ipSpaces;

  private final Map<String, Map<String, IpSpace>> _vrfOwnedIpSpaces;

  private final Set<String> _sourceInterfaces;

  public PathToACL(Map<String, Configuration> configs, ForwardingAnalysis forwardingAnalysis) {
    _acls = computeAcls(configs);
    _configs = configs;
    _forwardingAnalysis = forwardingAnalysis;
    _ipSpaces = computeIpSpaces(configs);
    _vrfOwnedIpSpaces =
        TopologyUtil.computeVrfOwnedIpSpaces(
            TopologyUtil.computeIpVrfOwners(true, TopologyUtil.computeNodeInterfaces(configs)));
    _sourceInterfaces = computeSourceInterfaces(_acls);
  }

  private static Function<String, String> renamer(String hostname) {
    return name -> String.format("%s:%s", hostname, name);
  }

  static Map<String, IpAccessList> computeAcls(Map<String, Configuration> configs) {
    ImmutableMap.Builder<String, IpAccessList> acls = ImmutableMap.builder();
    configs
        .values()
        .forEach(
            config -> {
              Function<String, String> renamer = renamer(config.getHostname());
              IpSpaceRenamer ipSpaceRenamer = new IpSpaceRenamer(renamer);
              IpAccessListRenamer aclRenamer =
                  new IpAccessListRenamer(renamer, ipSpaceRenamer, renamer);
              config
                  .getIpAccessLists()
                  .forEach((name, acl) -> acls.put(renamer.apply(name), aclRenamer.apply(acl)));
            });
    return acls.build();
  }

  static Map<String, IpSpace> computeIpSpaces(Map<String, Configuration> configs) {
    ImmutableMap.Builder<String, IpSpace> ipSpaces = ImmutableMap.builder();
    configs
        .values()
        .forEach(
            config -> {
              Function<String, String> renamer = renamer(config.getHostname());
              IpSpaceRenamer ipSpaceRenamer = new IpSpaceRenamer(renamer);
              config
                  .getIpSpaces()
                  .forEach(
                      (name, ipSpace) -> {
                        ipSpaces.put(renamer.apply(name), ipSpaceRenamer.apply(ipSpace));
                      });
            });
    return ipSpaces.build();
  }

  /* requirement: MatchSrcInterfaces in acls have been renamed to make each source
   * interface name globally unique (i.e. qualified by node name).
   */
  static Set<String> computeSourceInterfaces(Map<String, IpAccessList> acls) {
    return acls.values()
        .stream()
        .flatMap(
            acl ->
                acl.getLines()
                    .stream()
                    .map(IpAccessListLine::getMatchCondition)
                    .map(AclLineMatchExprLiterals::getLiterals)
                    .flatMap(Set::stream)
                    .filter(MatchSrcInterface.class::isInstance)
                    .map(MatchSrcInterface.class::cast)
                    .map(MatchSrcInterface::getSrcInterfaces)
                    .flatMap(Set::stream))
        .collect(ImmutableSet.toImmutableSet());
  }

  static Path toPath(Trace trace, Map<String, Configuration> configs) {
    Path path = null;
    NodeInterfacePair nextHopInterface = null;
    for (Hop hop : Lists.reverse(trace.getHops())) {
      path = toPath(configs.get(hop.getNode().getName()), hop, nextHopInterface, path);
      nextHopInterface = hopInputNodeInterface(hop);
    }
    return path;
  }

  static String hopVrf(Configuration node, Hop hop) {
    Step<?> firstStep = hop.getSteps().get(0);
    checkArgument(firstStep instanceof EnterInputIfaceStep || firstStep instanceof OriginateStep);
    if (firstStep instanceof EnterInputIfaceStep) {
      EnterInputIfaceStepDetail detail = (EnterInputIfaceStepDetail) firstStep.getDetail();
      return node.getAllInterfaces().get(detail.getInputInterface().getInterface()).getVrfName();
    } else {
      OriginateStepDetail detail = (OriginateStepDetail) firstStep.getDetail();
      return detail.getOriginatingVrf();
    }
  }

  static @Nullable NodeInterfacePair hopInputNodeInterface(Hop nextHop) {
    Step<?> firstStep = nextHop.getSteps().get(0);
    checkArgument(firstStep instanceof EnterInputIfaceStep || firstStep instanceof OriginateStep);
    if (firstStep instanceof EnterInputIfaceStep) {
      EnterInputIfaceStepDetail detail = (EnterInputIfaceStepDetail) firstStep.getDetail();
      return detail.getInputInterface();
    } else {
      return null;
    }
  }

  static Path toPath(
      Configuration node, Hop hop, @Nullable NodeInterfacePair nextHop, @Nullable Path next) {
    String hostname = node.getHostname();
    String vrf = hopVrf(node, hop);

    Function<String, String> renamer = renamer(hostname);
    Path path = next;

    for (Step<?> step : Lists.reverse(hop.getSteps())) {
      if (step instanceof InboundStep) {
        path = new Path(new AcceptStep(hostname, vrf), path);
      }
      if (step instanceof RoutingStep) {
        RoutingStepDetail detail = (RoutingStepDetail) step.getDetail();
        if (step.getAction() == StepAction.NO_ROUTE) {
          checkState(path == null, "NO_ROUTE should be the last step in a path");
          checkState(nextHop == null, "There should be no nextHop for NO_ROUTE");
          path = new Path(new NoRouteStep(hostname, vrf), null);
        } else {
          path =
              new Path(new RouteStep(hostname, vrf, detail.getRoutes().get(0).getNetwork()), path);
        }
      } else if (step instanceof FilterStep) {
        FilterStepDetail detail = (FilterStepDetail) step.getDetail();
        String aclName = renamer.apply(detail.getFilter());
        path = new Path(new AclStep(aclName, lineAction(step.getAction())), path);
      }
    }
    return path;
  }

  private static LineAction lineAction(StepAction stepAction) {
    switch (stepAction) {
      case PERMITTED:
        return PERMIT;
      case DENIED:
        return DENY;
      default:
        throw new IllegalArgumentException(stepAction.toString());
    }
  }

  private static FlowDisposition disposition(StepAction stepAction) {
    switch (stepAction) {
      case DELIVERED_TO_SUBNET:
        return FlowDisposition.DELIVERED_TO_SUBNET;
      case EXITS_NETWORK:
        return FlowDisposition.EXITS_NETWORK;
      case NEIGHBOR_UNREACHABLE:
        return FlowDisposition.NEIGHBOR_UNREACHABLE;
      case INSUFFICIENT_INFO:
        return FlowDisposition.INSUFFICIENT_INFO;
      default:
        throw new IllegalArgumentException(stepAction.toString());
    }
  }

  public static final class Path {
    private final PathStep _step;
    private final @Nullable Path _next;

    public Path(PathStep step, @Nullable Path next) {
      _step = step;
      _next = next;
    }
  }

  public interface PathStep {
    <T> T accept(PathStepVisitor<T> visitor);
  }

  public interface PathStepVisitor<T> {
    T visitAcceptStep(AcceptStep acceptStep);

    T visitAclStep(AclStep aclStep);

    T visitArpFailureStep(ArpFailureStep arpFailureStep);

    T visitNoRouteStep(NoRouteStep noRouteStep);

    T visitRouteStep(RouteStep routeStep);
  }

  public static final class AcceptStep implements PathStep {
    private final String _hostname;
    private final String _vrf;

    public AcceptStep(String hostname, String vrf) {
      _hostname = hostname;
      _vrf = vrf;
    }

    @Override
    public <T> T accept(PathStepVisitor<T> visitor) {
      return visitor.visitAcceptStep(this);
    }
  }

  public static final class AclStep implements PathStep {
    private final String _aclName;
    private final LineAction _action;

    public AclStep(String aclName, LineAction action) {
      _aclName = aclName;
      _action = action;
    }

    @Override
    public <T> T accept(PathStepVisitor<T> visitor) {
      return visitor.visitAclStep(this);
    }
  }

  public static final class NoRouteStep implements PathStep {
    private final String _node;
    private final String _vrf;

    public NoRouteStep(String node, String vrf) {
      _node = node;
      _vrf = vrf;
    }

    @Override
    public <T> T accept(PathStepVisitor<T> visitor) {
      return visitor.visitNoRouteStep(this);
    }
  }

  public static final class RouteStep implements PathStep {
    private final String _node;
    private final String _vrf;
    private final Prefix _network;

    public RouteStep(String node, String vrf, Prefix network) {
      _node = node;
      _vrf = vrf;
      _network = network;
    }

    @Override
    public <T> T accept(PathStepVisitor<T> visitor) {
      return visitor.visitRouteStep(this);
    }
  }

  private static final List<FlowDisposition> ARP_FAILURE_DISPOSITIONS =
      ImmutableList.of(DELIVERED_TO_SUBNET, EXITS_NETWORK, NEIGHBOR_UNREACHABLE, INSUFFICIENT_INFO);

  private static final class ArpFailureStep implements PathStep {
    private final String _node;
    private final String _vrf;
    private final String _iface;
    private final FlowDisposition _disposition;

    private ArpFailureStep(String node, String vrf, String iface, FlowDisposition disposition) {
      checkArgument(ARP_FAILURE_DISPOSITIONS.contains(disposition));
      _node = node;
      _vrf = vrf;
      _iface = iface;
      _disposition = disposition;
    }

    @Override
    public <T> T accept(PathStepVisitor<T> visitor) {
      return visitor.visitArpFailureStep(this);
    }
  }

  public AclLineMatchExpr traceMatchExpr(Trace trace) {
    BDDPacket bddPacket = new BDDPacket();
    AclLineMatchExpr pathMatchExpr = pathMatchExpr(toPath(trace, _configs));
    return AclExplainer.explain(
        bddPacket,
        BDDSourceManager.forInterfaces(bddPacket, _sourceInterfaces),
        pathMatchExpr,
        IpAccessList.builder()
            .setName("~~~~~")
            .setLines(ImmutableList.of(IpAccessListLine.ACCEPT_ALL))
            .build(),
        _acls,
        _ipSpaces);
  }

  AclLineMatchExpr pathMatchExpr(Path path) {
    ImmutableList.Builder<AclLineMatchExpr> conjuncts = ImmutableList.builder();
    while (path != null) {
      conjuncts.add(path._step.accept(PATH_STEP_TO_ACL_EXPR));
      path = path._next;
    }
    return new AndMatchExpr(conjuncts.build());
  }

  GenericIpSpaceVisitor<AclLineMatchExpr> DST_IP_SPACE_MATCH_EXPR =
      new GenericIpSpaceVisitor<AclLineMatchExpr>() {
        @Override
        public AclLineMatchExpr castToGenericIpSpaceVisitorReturnType(Object o) {
          return null;
        }

        @Override
        public AclLineMatchExpr visitAclIpSpace(AclIpSpace aclIpSpace) {
          return null;
        }

        @Override
        public AclLineMatchExpr visitEmptyIpSpace(EmptyIpSpace emptyIpSpace) {
          return null;
        }

        @Override
        public AclLineMatchExpr visitIpIpSpace(IpIpSpace ipIpSpace) {
          return null;
        }

        @Override
        public AclLineMatchExpr visitIpSpaceReference(IpSpaceReference ipSpaceReference) {
          return null;
        }

        @Override
        public AclLineMatchExpr visitIpWildcardIpSpace(IpWildcardIpSpace ipWildcardIpSpace) {
          return null;
        }

        @Override
        public AclLineMatchExpr visitIpWildcardSetIpSpace(
            IpWildcardSetIpSpace ipWildcardSetIpSpace) {
          ImmutableSortedSet.Builder<AclLineMatchExpr> conjuncts =
              ImmutableSortedSet.naturalOrder();
          conjuncts.add(
              AclLineMatchExprs.or(
                  ipWildcardSetIpSpace
                      .getWhitelist()
                      .stream()
                      .map(AclLineMatchExprs::matchDst)
                      .collect(Collectors.toList())));
          ipWildcardSetIpSpace
              .getBlacklist()
              .stream()
              .map(AclLineMatchExprs::matchDst)
              .map(AclLineMatchExprs::not)
              .forEach(conjuncts::add);
          return new AndMatchExpr(conjuncts.build());
        }

        @Override
        public AclLineMatchExpr visitPrefixIpSpace(PrefixIpSpace prefixIpSpace) {
          return null;
        }

        @Override
        public AclLineMatchExpr visitUniverseIpSpace(UniverseIpSpace universeIpSpace) {
          return null;
        }
      };

  PathStepVisitor<AclLineMatchExpr> PATH_STEP_TO_ACL_EXPR =
      new PathStepVisitor<AclLineMatchExpr>() {
        @Override
        public AclLineMatchExpr visitAcceptStep(AcceptStep acceptStep) {
          return matchDst(_vrfOwnedIpSpaces.get(acceptStep._hostname).get(acceptStep._vrf));
        }

        @Override
        public AclLineMatchExpr visitAclStep(AclStep aclStep) {
          PermittedByAcl permitted = permittedByAcl(aclStep._aclName);
          return aclStep._action == PERMIT ? permitted : not(permitted);
        }

        @Override
        public AclLineMatchExpr visitNoRouteStep(NoRouteStep noRouteStep) {
          return matchDst(
              _forwardingAnalysis
                  .getRoutableIps()
                  .get(noRouteStep._node)
                  .get(noRouteStep._vrf)
                  .complement());
        }

        @Override
        public AclLineMatchExpr visitRouteStep(RouteStep routeStep) {
          IpSpace ipSpace =
              _forwardingAnalysis
                  .getMatchingIps()
                  .get(routeStep._node)
                  .get(routeStep._vrf)
                  .get(routeStep._network);
          return Optional.ofNullable(DST_IP_SPACE_MATCH_EXPR.visit(ipSpace))
              .orElseGet(() -> matchDst(ipSpace));
        }

        @Override
        public AclLineMatchExpr visitArpFailureStep(ArpFailureStep arpFailureStep) {
          return matchDst(
              getArpFailureIpSpaces(arpFailureStep)
                  .get(arpFailureStep._node)
                  .get(arpFailureStep._vrf)
                  .get(arpFailureStep._iface));
        }

        private Map<String, Map<String, Map<String, IpSpace>>> getArpFailureIpSpaces(
            ArpFailureStep arpFailureStep) {
          switch (arpFailureStep._disposition) {
            case DELIVERED_TO_SUBNET:
              return _forwardingAnalysis.getDeliveredToSubnet();
            case EXITS_NETWORK:
              return _forwardingAnalysis.getExitsNetwork();
            case NEIGHBOR_UNREACHABLE:
              return _forwardingAnalysis.getNeighborUnreachable();
            case INSUFFICIENT_INFO:
              return _forwardingAnalysis.getInsufficientInfo();
            default:
              throw new IllegalArgumentException(
                  "Unexpected ARP failure disposition: " + arpFailureStep._disposition);
          }
        }
      };
}
