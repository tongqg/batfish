package org.batfish.datamodel.ospf;

import static com.google.common.base.MoreObjects.firstNonNull;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSortedMap;
import com.google.common.collect.ImmutableSortedSet;
import com.google.common.collect.Ordering;
import com.google.common.collect.Streams;
import java.io.Serializable;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Stream;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;
import org.batfish.datamodel.Configuration;
import org.batfish.datamodel.ConfigurationFormat;
import org.batfish.datamodel.GeneratedRoute;
import org.batfish.datamodel.Interface;
import org.batfish.datamodel.Ip;
import org.batfish.datamodel.IpLink;
import org.batfish.datamodel.NetworkFactory;
import org.batfish.datamodel.NetworkFactory.NetworkFactoryBuilder;
import org.batfish.datamodel.RoutingProtocol;
import org.batfish.datamodel.Vrf;
import org.batfish.datamodel.routing_policy.RoutingPolicy;

/** An OSPF routing process */
@ParametersAreNonnullByDefault
public final class OspfProcess implements Serializable {

  /** Builder for {@link OspfProcess} */
  public static class Builder extends NetworkFactoryBuilder<OspfProcess> {

    @Nullable private SortedMap<RoutingProtocol, Integer> _adminCosts;
    @Nullable private String _exportPolicy;
    @Nullable private Long _maxMetricExternalNetworks;
    @Nullable private Long _maxMetricStubNetworks;
    @Nullable private Long _maxMetricSummaryNetworks;
    @Nullable private Long _maxMetricTransitLinks;
    @Nullable private Map<String, OspfNeighborConfig> _neighbors;
    @Nullable private String _processId;
    @Nullable private Double _referenceBandwidth;
    @Nullable private Vrf _vrf;
    @Nullable private SortedMap<Long, OspfArea> _areas;
    @Nullable private SortedSet<String> _exportPolicySources;
    @Nullable private SortedSet<GeneratedRoute> _generatedRoutes;
    @Nullable private Boolean _rfc1583Compatible;
    @Nullable private Ip _routerId;
    @Nullable private Integer _summaryAdminCost;

    private Builder(@Nullable NetworkFactory networkFactory) {
      super(networkFactory, OspfProcess.class);
    }

    @Override
    public OspfProcess build() {
      OspfProcess ospfProcess =
          new OspfProcess(
              _adminCosts,
              _areas,
              _exportPolicy,
              _exportPolicySources,
              _generatedRoutes,
              _maxMetricExternalNetworks,
              _maxMetricStubNetworks,
              _maxMetricSummaryNetworks,
              _maxMetricTransitLinks,
              _neighbors,
              _processId != null ? _processId : generateName(),
              _referenceBandwidth,
              _rfc1583Compatible,
              _routerId,
              _summaryAdminCost);
      if (_vrf != null) {
        _vrf.setOspfProcesses(
            Streams.concat(_vrf.getOspfProcesses().values().stream(), Stream.of(ospfProcess)));
      }
      return ospfProcess;
    }

    public Builder setAdminCosts(@Nonnull Map<RoutingProtocol, Integer> adminCosts) {
      _adminCosts = ImmutableSortedMap.copyOf(adminCosts);
      return this;
    }

    public Builder setExportPolicy(@Nullable RoutingPolicy exportPolicy) {
      _exportPolicy = exportPolicy != null ? exportPolicy.getName() : null;
      return this;
    }

    public Builder setMaxMetricExternalNetworks(Long maxMetricExternalNetworks) {
      _maxMetricExternalNetworks = maxMetricExternalNetworks;
      return this;
    }

    public Builder setMaxMetricStubNetworks(Long maxMetricStubNetworks) {
      _maxMetricStubNetworks = maxMetricStubNetworks;
      return this;
    }

    public Builder setMaxMetricSummaryNetworks(Long maxMetricSummaryNetworks) {
      _maxMetricSummaryNetworks = maxMetricSummaryNetworks;
      return this;
    }

    public Builder setMaxMetricTransitLinks(Long maxMetricTransitLinks) {
      _maxMetricTransitLinks = maxMetricTransitLinks;
      return this;
    }

    public Builder setNeighbors(Map<String, OspfNeighborConfig> neighbors) {
      _neighbors = neighbors;
      return this;
    }

    public Builder setProcessId(@Nonnull String processId) {
      _processId = processId;
      return this;
    }

    public Builder setReferenceBandwidth(Double referenceBandwidth) {
      _referenceBandwidth = referenceBandwidth;
      return this;
    }

    public Builder setVrf(Vrf vrf) {
      _vrf = vrf;
      return this;
    }

    public Builder setAreas(SortedMap<Long, OspfArea> areas) {
      _areas = areas;
      return this;
    }

    public Builder setExportPolicyName(@Nullable String exportPolicy) {
      _exportPolicy = exportPolicy;
      return this;
    }

    public Builder setExportPolicySources(SortedSet<String> exportPolicySources) {
      _exportPolicySources = exportPolicySources;
      return this;
    }

    public Builder setGeneratedRoutes(SortedSet<GeneratedRoute> generatedRoutes) {
      _generatedRoutes = generatedRoutes;
      return this;
    }

    public Builder setRfc1583Compatible(Boolean rfc1583Compatible) {
      _rfc1583Compatible = rfc1583Compatible;
      return this;
    }

    public Builder setRouterId(Ip routerId) {
      _routerId = routerId;
      return this;
    }

    public Builder setSummaryAdminCost(int admin) {
      _summaryAdminCost = admin;
      return this;
    }
  }

  private static final int DEFAULT_CISCO_VLAN_OSPF_COST = 1;
  /** Set of routing protocols that are required to have a defined admin cost */
  public static final EnumSet<RoutingProtocol> REQUIRES_ADMIN =
      EnumSet.of(
          RoutingProtocol.OSPF,
          RoutingProtocol.OSPF_IA,
          RoutingProtocol.OSPF_E1,
          RoutingProtocol.OSPF_E2);

  private static final String PROP_ADMIN_COSTS = "adminCosts";
  private static final String PROP_AREAS = "areas";
  private static final String PROP_EXPORT_POLICY = "exportPolicy";
  private static final String PROP_EXPORT_POLICY_SOURCES = "exportPolicySources";
  private static final String PROP_GENERATED_ROUTES = "generatedRoutes";
  private static final String PROP_MAX_METRIC_EXTERNAL_NETWORKS = "maxMetricExternalNetworks";
  private static final String PROP_MAX_METRIC_STUB_NETWORKS = "maxMetricStubNetworks";
  private static final String PROP_MAX_METRIC_SUMMARY_NETWORKS = "maxMetricSummaryNetworks";
  private static final String PROP_MAX_METRIC_TRANSIT_LINKS = "maxMetricTransitLinks";
  private static final String PROP_NEIGHBORS = "neighbors";
  private static final String PROP_PROCESS_ID = "processId";
  private static final String PROP_REFERENCE_BANDWIDTH = "referenceBandwidth";
  private static final String PROP_ROUTER_ID = "routerId";
  private static final String PROP_RFC1583 = "rfc1583Compatible";
  private static final String PROP_SUMMARY_ADMIN = "summaryAdminCost";

  /** Builder to be used for tests (maintains pointer to {@link NetworkFactory} */
  public static Builder builder(@Nullable NetworkFactory networkFactory) {
    return new Builder(networkFactory);
  }

  public static Builder builder() {
    return new Builder(null);
  }

  @Nonnull private final SortedMap<RoutingProtocol, Integer> _adminCosts;

  @Nonnull private SortedMap<Long, OspfArea> _areas;
  @Nullable private String _exportPolicy;
  @Nonnull private SortedSet<String> _exportPolicySources;
  @Nonnull private SortedSet<GeneratedRoute> _generatedRoutes;
  @Nullable private Long _maxMetricExternalNetworks;
  @Nullable private Long _maxMetricStubNetworks;
  @Nullable private Long _maxMetricSummaryNetworks;
  @Nullable private Long _maxMetricTransitLinks;
  private transient Map<IpLink, OspfNeighbor> _ospfNeighbors;
  /** Mapping from interface name to an OSPF config */
  @Nonnull private SortedMap<String, OspfNeighborConfig> _ospfNeighborConfigs;

  @Nonnull private final String _processId;
  @Nonnull private Double _referenceBandwidth;
  @Nullable private Boolean _rfc1583Compatible;
  @Nullable private Ip _routerId;
  private int _summaryAdminCost;

  @JsonCreator
  private OspfProcess(
      @Nullable @JsonProperty(PROP_ADMIN_COSTS) SortedMap<RoutingProtocol, Integer> adminCosts,
      @Nullable @JsonProperty(PROP_AREAS) SortedMap<Long, OspfArea> areas,
      @Nullable @JsonProperty(PROP_EXPORT_POLICY) String exportPolicy,
      @Nullable @JsonProperty(PROP_EXPORT_POLICY_SOURCES) SortedSet<String> exportPolicySources,
      @Nullable @JsonProperty(PROP_GENERATED_ROUTES) SortedSet<GeneratedRoute> generatedRoutes,
      @Nullable @JsonProperty(PROP_MAX_METRIC_EXTERNAL_NETWORKS) Long maxMetricExternalNetworks,
      @Nullable @JsonProperty(PROP_MAX_METRIC_STUB_NETWORKS) Long maxMetricStubNetworks,
      @Nullable @JsonProperty(PROP_MAX_METRIC_SUMMARY_NETWORKS) Long maxMetricSummaryNetworks,
      @Nullable @JsonProperty(PROP_MAX_METRIC_TRANSIT_LINKS) Long maxMetricTransitLinks,
      @Nullable @JsonProperty(PROP_NEIGHBORS) Map<String, OspfNeighborConfig> neighbors,
      @Nullable @JsonProperty(PROP_PROCESS_ID) String processId,
      @Nullable @JsonProperty(PROP_REFERENCE_BANDWIDTH) Double referenceBandwidth,
      @Nullable @JsonProperty(PROP_RFC1583) Boolean rfc1583Compatible,
      @Nullable @JsonProperty(PROP_ROUTER_ID) Ip routerId,
      @Nullable @JsonProperty(PROP_SUMMARY_ADMIN) Integer summaryAdminCost) {
    checkArgument(processId != null, "Missing %s", PROP_PROCESS_ID);
    checkArgument(referenceBandwidth != null, "Missing %s", PROP_REFERENCE_BANDWIDTH);
    _adminCosts =
        ImmutableSortedMap.copyOf(
            firstNonNull(
                adminCosts,
                // In the absence of provided values, default to Cisco IOS values
                computeDefaultAdminCosts(ConfigurationFormat.CISCO_IOS)));
    checkArgument(_adminCosts.keySet().containsAll(REQUIRES_ADMIN));
    _areas = firstNonNull(areas, ImmutableSortedMap.of());
    // Ensure area numbers match up
    checkArgument(
        _areas.entrySet().stream()
            .allMatch(entry -> entry.getKey() == entry.getValue().getAreaNumber()),
        "Area number does not match up with map key");
    _exportPolicy = exportPolicy;
    _exportPolicySources = firstNonNull(exportPolicySources, ImmutableSortedSet.of());
    _generatedRoutes = firstNonNull(generatedRoutes, ImmutableSortedSet.of());
    _maxMetricExternalNetworks = maxMetricExternalNetworks;
    _maxMetricStubNetworks = maxMetricStubNetworks;
    _maxMetricSummaryNetworks = maxMetricSummaryNetworks;
    _maxMetricTransitLinks = maxMetricTransitLinks;
    _ospfNeighbors = new TreeMap<>();
    _ospfNeighborConfigs =
        ImmutableSortedMap.copyOf(firstNonNull(neighbors, ImmutableSortedMap.of()));
    _processId = processId;
    _referenceBandwidth = referenceBandwidth;
    _rfc1583Compatible = rfc1583Compatible;
    _routerId = routerId;
    // In the absence of provided value, default to Cisco IOS value
    _summaryAdminCost =
        firstNonNull(
            summaryAdminCost,
            RoutingProtocol.OSPF_IA.getSummaryAdministrativeCost(ConfigurationFormat.CISCO_IOS));
  }

  /** Compute default admin costs based on a given configuration format */
  public static Map<RoutingProtocol, Integer> computeDefaultAdminCosts(ConfigurationFormat format) {
    return REQUIRES_ADMIN.stream()
        .collect(
            ImmutableMap.toImmutableMap(
                Function.identity(), rp -> rp.getDefaultAdministrativeCost(format)));
  }

  public int computeInterfaceCost(Interface i) {
    return computeInterfaceCost(_referenceBandwidth, i);
  }

  @VisibleForTesting
  static int computeInterfaceCost(Double referenceBandwidth, Interface i) {
    Integer ospfCost = i.getOspfCost();
    if (ospfCost != null) {
      return ospfCost;
    }

    String interfaceName = i.getName();
    if (interfaceName.startsWith("Vlan")) {
      // Special handling for VLAN interfaces
      // TODO: fix for non-cisco
      return DEFAULT_CISCO_VLAN_OSPF_COST;
    } else {
      // Regular physical interface cost computation
      checkState(
          i.getBandwidth() != null,
          "Interface %s on %s is missing bandwidth, cannot compute OSPF cost",
          interfaceName,
          i.getOwner().getHostname());
      return Math.max((int) (referenceBandwidth / i.getBandwidth()), 1);
    }
  }

  /**
   * The admin costs assigned to routes by this process, for each OSPF routing protocol (see {@link
   * #REQUIRES_ADMIN})
   */
  @Nonnull
  @JsonProperty(PROP_ADMIN_COSTS)
  public SortedMap<RoutingProtocol, Integer> getAdminCosts() {
    return _adminCosts;
  }

  /** The OSPF areas contained in this process */
  @Nonnull
  @JsonProperty(PROP_AREAS)
  public SortedMap<Long, OspfArea> getAreas() {
    return _areas;
  }

  /**
   * The routing policy applied to routes in the main RIB to determine which ones are to be exported
   * into OSPF and how
   */
  @JsonProperty(PROP_EXPORT_POLICY)
  @Nullable
  public String getExportPolicy() {
    return _exportPolicy;
  }

  /**
   * Return the names of policies that contribute to the unified export policy. The resulting set is
   * immutable.
   */
  @Nonnull
  @JsonProperty(PROP_EXPORT_POLICY_SOURCES)
  public SortedSet<String> getExportPolicySources() {
    return _exportPolicySources;
  }

  /**
   * Generated IPV4 routes for the purpose of export into OSPF. These routes are not imported into
   * the main RIB.
   */
  @Nonnull
  @JsonProperty(PROP_GENERATED_ROUTES)
  public SortedSet<GeneratedRoute> getGeneratedRoutes() {
    return _generatedRoutes;
  }

  @Nullable
  @JsonProperty(PROP_MAX_METRIC_EXTERNAL_NETWORKS)
  public Long getMaxMetricExternalNetworks() {
    return _maxMetricExternalNetworks;
  }

  @Nullable
  @JsonProperty(PROP_MAX_METRIC_STUB_NETWORKS)
  public Long getMaxMetricStubNetworks() {
    return _maxMetricStubNetworks;
  }

  @Nullable
  @JsonProperty(PROP_MAX_METRIC_SUMMARY_NETWORKS)
  public Long getMaxMetricSummaryNetworks() {
    return _maxMetricSummaryNetworks;
  }

  @Nullable
  @JsonProperty(PROP_MAX_METRIC_TRANSIT_LINKS)
  public Long getMaxMetricTransitLinks() {
    return _maxMetricTransitLinks;
  }

  @JsonIgnore
  public Map<IpLink, OspfNeighbor> getOspfNeighbors() {
    return _ospfNeighbors;
  }

  @JsonProperty(PROP_NEIGHBORS)
  public Map<String, OspfNeighborConfig> getOspfNeighborConfigs() {
    return _ospfNeighborConfigs;
  }

  @Nonnull
  @JsonProperty(PROP_PROCESS_ID)
  public String getProcessId() {
    return _processId;
  }

  /**
   * The reference bandwidth by which an interface's bandwidth is divided to determine its OSPF cost
   */
  @Nonnull
  @JsonProperty(PROP_REFERENCE_BANDWIDTH)
  public Double getReferenceBandwidth() {
    return _referenceBandwidth;
  }

  @Nullable
  @JsonProperty(PROP_RFC1583)
  public Boolean getRfc1583Compatible() {
    return _rfc1583Compatible;
  }

  /** The router-id of this OSPF process */
  @Nullable
  @JsonProperty(PROP_ROUTER_ID)
  public Ip getRouterId() {
    return _routerId;
  }

  /** Return the admin cost assigned to inter-area summaries */
  public int getSummaryAdminCost() {
    return _summaryAdminCost;
  }

  /** Initialize interface costs for all interfaces that belong to this process */
  public void initInterfaceCosts(Configuration c) {
    _areas.values().stream()
        .flatMap(a -> a.getInterfaces().stream())
        .map(interfaceName -> c.getAllInterfaces().get(interfaceName))
        .filter(Interface::getActive)
        .forEach(i -> i.setOspfCost(computeInterfaceCost(i)));
  }

  /**
   * Check if this process serves as and ABR (Area Border Router). An ABR has at least one interface
   * in area 0, and at least one interface in another area.
   */
  @JsonIgnore
  public boolean isAreaBorderRouter() {
    Set<Long> areas = _areas.keySet();
    return areas.contains(0L) && areas.size() > 1;
  }

  public void setAreas(SortedMap<Long, OspfArea> areas) {
    // Ensure area numbers match up
    checkArgument(
        areas.entrySet().stream()
            .allMatch(entry -> entry.getKey() == entry.getValue().getAreaNumber()),
        "Area number does not match up with map key");
    _areas = areas;
  }

  public void addArea(OspfArea area) {
    _areas =
        ImmutableSortedMap.<Long, OspfArea>naturalOrder()
            .putAll(_areas)
            .put(area.getAreaNumber(), area)
            .build();
  }

  public void setExportPolicy(@Nullable String exportPolicy) {
    _exportPolicy = exportPolicy;
  }

  public void setExportPolicySources(SortedSet<String> exportPolicySources) {
    _exportPolicySources = ImmutableSortedSet.copyOf(exportPolicySources);
  }

  /**
   * Overwrite all generated route. See {@link #getGeneratedRoutes} for explanation of generated
   * routes.
   */
  public void setGeneratedRoutes(SortedSet<GeneratedRoute> generatedRoutes) {
    _generatedRoutes = ImmutableSortedSet.copyOf(generatedRoutes);
  }

  /** Add a generated route. See {@link #getGeneratedRoutes} for explanation of generated routes. */
  public void addGeneratedRoute(GeneratedRoute route) {
    _generatedRoutes =
        new ImmutableSortedSet.Builder<GeneratedRoute>(Ordering.natural())
            .addAll(_generatedRoutes)
            .add(route)
            .build();
  }

  public void setMaxMetricExternalNetworks(@Nullable Long maxMetricExternalNetworks) {
    _maxMetricExternalNetworks = maxMetricExternalNetworks;
  }

  public void setMaxMetricStubNetworks(@Nullable Long maxMetricStubNetworks) {
    _maxMetricStubNetworks = maxMetricStubNetworks;
  }

  public void setMaxMetricSummaryNetworks(@Nullable Long maxMetricSummaryNetworks) {
    _maxMetricSummaryNetworks = maxMetricSummaryNetworks;
  }

  public void setMaxMetricTransitLinks(@Nullable Long maxMetricTransitLinks) {
    _maxMetricTransitLinks = maxMetricTransitLinks;
  }

  @JsonIgnore
  public void setOspfNeighbors(Map<IpLink, OspfNeighbor> ospfNeighbors) {
    _ospfNeighbors = ospfNeighbors;
  }

  void setOspfNeighborConfigs(Map<String, OspfNeighborConfig> ospfNeighborConfigs) {
    _ospfNeighborConfigs = ImmutableSortedMap.copyOf(ospfNeighborConfigs);
  }

  public void setReferenceBandwidth(Double referenceBandwidth) {
    _referenceBandwidth = referenceBandwidth;
  }

  public void setRfc1583Compatible(@Nullable Boolean rfc1583Compatible) {
    _rfc1583Compatible = rfc1583Compatible;
  }

  public void setRouterId(Ip id) {
    _routerId = id;
  }
}
