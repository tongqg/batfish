package org.batfish.representation.aws;

import static com.google.common.base.MoreObjects.firstNonNull;
import static com.google.common.base.Preconditions.checkArgument;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.MoreObjects;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSortedMap;
import com.google.common.collect.ImmutableSortedSet;
import java.io.IOException;
import java.io.Serializable;
import java.io.StringReader;
import java.util.List;
import java.util.Objects;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.batfish.common.BatfishException;
import org.batfish.common.Warnings;
import org.batfish.datamodel.BgpActivePeerConfig;
import org.batfish.datamodel.BgpProcess;
import org.batfish.datamodel.ConcreteInterfaceAddress;
import org.batfish.datamodel.Configuration;
import org.batfish.datamodel.ConfigurationFormat;
import org.batfish.datamodel.DiffieHellmanGroup;
import org.batfish.datamodel.EncryptionAlgorithm;
import org.batfish.datamodel.IkeAuthenticationMethod;
import org.batfish.datamodel.IkeHashingAlgorithm;
import org.batfish.datamodel.IkeKeyType;
import org.batfish.datamodel.IkePhase1Key;
import org.batfish.datamodel.IkePhase1Policy;
import org.batfish.datamodel.IkePhase1Proposal;
import org.batfish.datamodel.Ip;
import org.batfish.datamodel.IpsecAuthenticationAlgorithm;
import org.batfish.datamodel.IpsecEncapsulationMode;
import org.batfish.datamodel.IpsecPeerConfig;
import org.batfish.datamodel.IpsecPhase2Policy;
import org.batfish.datamodel.IpsecPhase2Proposal;
import org.batfish.datamodel.IpsecProtocol;
import org.batfish.datamodel.IpsecStaticPeerConfig;
import org.batfish.datamodel.LineAction;
import org.batfish.datamodel.MultipathEquivalentAsPathMatchMode;
import org.batfish.datamodel.OriginType;
import org.batfish.datamodel.Prefix;
import org.batfish.datamodel.RouteFilterLine;
import org.batfish.datamodel.RouteFilterList;
import org.batfish.datamodel.RoutingProtocol;
import org.batfish.datamodel.StaticRoute;
import org.batfish.datamodel.SubRange;
import org.batfish.datamodel.bgp.AddressFamilyCapabilities;
import org.batfish.datamodel.bgp.Ipv4UnicastAddressFamily;
import org.batfish.datamodel.routing_policy.RoutingPolicy;
import org.batfish.datamodel.routing_policy.expr.Conjunction;
import org.batfish.datamodel.routing_policy.expr.DestinationNetwork;
import org.batfish.datamodel.routing_policy.expr.LiteralOrigin;
import org.batfish.datamodel.routing_policy.expr.MatchPrefixSet;
import org.batfish.datamodel.routing_policy.expr.MatchProtocol;
import org.batfish.datamodel.routing_policy.expr.NamedPrefixSet;
import org.batfish.datamodel.routing_policy.expr.SelfNextHop;
import org.batfish.datamodel.routing_policy.statement.If;
import org.batfish.datamodel.routing_policy.statement.SetNextHop;
import org.batfish.datamodel.routing_policy.statement.SetOrigin;
import org.batfish.datamodel.routing_policy.statement.Statement;
import org.batfish.datamodel.routing_policy.statement.Statements;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

/** Represents an AWS VPN connection */
@JsonIgnoreProperties(ignoreUnknown = true)
@ParametersAreNonnullByDefault
final class VpnConnection implements AwsVpcEntity, Serializable {

  private static final int BGP_NEIGHBOR_DEFAULT_METRIC = 0;

  private static DiffieHellmanGroup toDiffieHellmanGroup(String perfectForwardSecrecy) {
    switch (perfectForwardSecrecy) {
      case "group2":
        return DiffieHellmanGroup.GROUP2;
      default:
        throw new BatfishException(
            "No conversion to Diffie-Hellman group for string: \"" + perfectForwardSecrecy + "\"");
    }
  }

  private static EncryptionAlgorithm toEncryptionAlgorithm(String encryptionProtocol) {
    switch (encryptionProtocol) {
      case "aes-128-cbc":
        return EncryptionAlgorithm.AES_128_CBC;
      default:
        throw new BatfishException(
            "No conversion to encryption algorithm for string: \"" + encryptionProtocol + "\"");
    }
  }

  private static IkeHashingAlgorithm toIkeAuthenticationAlgorithm(String ikeAuthProtocol) {
    switch (ikeAuthProtocol) {
      case "sha1":
        return IkeHashingAlgorithm.SHA1;

      default:
        throw new BatfishException(
            "No conversion to ike authentication algorithm for string: \""
                + ikeAuthProtocol
                + "\"");
    }
  }

  private static IpsecAuthenticationAlgorithm toIpsecAuthenticationAlgorithm(
      String ipsecAuthProtocol) {
    switch (ipsecAuthProtocol) {
      case "hmac-sha1-96":
        return IpsecAuthenticationAlgorithm.HMAC_SHA1_96;
      default:
        throw new BatfishException(
            "No conversion to ipsec authentication algorithm for string: \""
                + ipsecAuthProtocol
                + "\"");
    }
  }

  private static IpsecProtocol toIpsecProtocol(String ipsecProtocol) {
    switch (ipsecProtocol) {
      case "esp":
        return IpsecProtocol.ESP;
      default:
        throw new BatfishException(
            "No conversion to ipsec protocol for string: \"" + ipsecProtocol + "\"");
    }
  }

  @Nullable
  private static IpsecEncapsulationMode toIpsecEncapdulationMode(
      String ipsecEncapsulationMode, Warnings warnings) {
    switch (ipsecEncapsulationMode) {
      case "tunnel":
        return IpsecEncapsulationMode.TUNNEL;
      case "transport":
        return IpsecEncapsulationMode.TRANSPORT;
      default:
        warnings.redFlag(
            String.format("No IPsec encapsulation mode for string '%s'", ipsecEncapsulationMode));
        return null;
    }
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  @ParametersAreNonnullByDefault
  private static class VpnRoute {

    @JsonCreator
    private static VpnRoute create(
        @Nullable @JsonProperty(JSON_KEY_DESTINATION_CIDR_BLOCK) Prefix destinationCidrBlock) {
      checkArgument(
          destinationCidrBlock != null, "Destination CIDR block cannot be null in VpnRoute");
      return new VpnRoute(destinationCidrBlock);
    }

    @Nonnull private final Prefix _destinationCidrBlock;

    private VpnRoute(Prefix destinationCidrBlock) {
      _destinationCidrBlock = destinationCidrBlock;
    }

    @Nonnull
    Prefix getDestinationCidrBlock() {
      return _destinationCidrBlock;
    }
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  @ParametersAreNonnullByDefault
  private static class Options {

    @JsonCreator
    private static Options create(
        @Nullable @JsonProperty(JSON_KEY_STATIC_ROUTES_ONLY) Boolean staticRoutesOnly) {
      return new Options(firstNonNull(staticRoutesOnly, false));
    }

    private final boolean _staticRoutesOnly;

    private Options(boolean staticRoutesOnly) {
      _staticRoutesOnly = staticRoutesOnly;
    }

    boolean getStaticRoutesOnly() {
      return _staticRoutesOnly;
    }
  }

  @Nonnull private final String _customerGatewayId;

  @Nonnull private final List<IpsecTunnel> _ipsecTunnels;

  @Nonnull private final List<Prefix> _routes;

  @Nonnull private final boolean _staticRoutesOnly;

  @Nonnull private final List<VgwTelemetry> _vgwTelemetrys;

  @Nonnull private final String _vpnConnectionId;

  @Nonnull private final String _vpnGatewayId;

  @JsonCreator
  private static VpnConnection create(
      @Nullable @JsonProperty(JSON_KEY_VPN_CONNECTION_ID) String vpnConnectionId,
      @Nullable @JsonProperty(JSON_KEY_CUSTOMER_GATEWAY_ID) String customerGatewayId,
      @Nullable @JsonProperty(JSON_KEY_VPN_GATEWAY_ID) String vpnGatewayId,
      @Nullable @JsonProperty(JSON_KEY_CUSTOMER_GATEWAY_CONFIGURATION) String cgwConfiguration,
      @Nullable @JsonProperty(JSON_KEY_ROUTES) List<VpnRoute> routes,
      @Nullable @JsonProperty(JSON_KEY_VGW_TELEMETRY) List<VgwTelemetry> vgwTelemetrys,
      @Nullable @JsonProperty(JSON_KEY_OPTIONS) Options options) {
    checkArgument(vpnConnectionId != null, "VPN connection Id cannot be null");
    checkArgument(
        customerGatewayId != null, "Customer gateway Id cannot be null for VPN connection");
    checkArgument(vpnGatewayId != null, "VPN gateway Id cannot be null for VPN connection");
    checkArgument(
        cgwConfiguration != null,
        "Customer gateway configuration cannot be null for VPN connection");
    checkArgument(routes != null, "Route list cannot be null for VPN connection");
    checkArgument(vgwTelemetrys != null, "VGW telemetry cannot be null for VPN connection");
    checkArgument(options != null, "Options cannot be null for VPN connection");

    Document document;
    try {
      DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
      DocumentBuilder builder = factory.newDocumentBuilder();
      InputSource is = new InputSource(new StringReader(cgwConfiguration));
      document = builder.parse(is);
    } catch (ParserConfigurationException | SAXException | IOException e) {
      throw new IllegalArgumentException(
          "Could not parse XML for CustomerGatewayConfiguration for vpn connection "
              + vpnConnectionId
              + " "
              + e);
    }

    ImmutableList.Builder<IpsecTunnel> ipsecTunnels = new ImmutableList.Builder<>();

    Element vpnConnection = (Element) document.getElementsByTagName(XML_KEY_VPN_CONNECTION).item(0);
    NodeList nodeList = document.getElementsByTagName(XML_KEY_IPSEC_TUNNEL);

    for (int index = 0; index < nodeList.getLength(); index++) {
      Element ipsecTunnel = (Element) nodeList.item(index);
      ipsecTunnels.add(IpsecTunnel.create(ipsecTunnel, vpnConnection));
    }

    return new VpnConnection(
        vpnConnectionId,
        customerGatewayId,
        vpnGatewayId,
        ipsecTunnels.build(),
        routes.stream()
            .map(VpnRoute::getDestinationCidrBlock)
            .collect(ImmutableList.toImmutableList()),
        vgwTelemetrys,
        options.getStaticRoutesOnly());
  }

  VpnConnection(
      String vpnConnectionId,
      String customerGatewayId,
      String vpnGatewayId,
      List<IpsecTunnel> ipsecTunnels,
      List<Prefix> routes,
      List<VgwTelemetry> vgwTelemetrys,
      boolean staticRoutesOnly) {
    _vpnConnectionId = vpnConnectionId;
    _customerGatewayId = customerGatewayId;
    _vpnGatewayId = vpnGatewayId;
    _ipsecTunnels = ipsecTunnels;
    _routes = routes;
    _vgwTelemetrys = vgwTelemetrys;
    _staticRoutesOnly = staticRoutesOnly;
  }

  @Nonnull
  private static IkePhase1Proposal toIkePhase1Proposal(
      String proposalName, IpsecTunnel ipsecTunnel) {
    IkePhase1Proposal ikePhase1Proposal = new IkePhase1Proposal(proposalName);
    if (ipsecTunnel.getIkePreSharedKeyHash() != null) {
      ikePhase1Proposal.setAuthenticationMethod(IkeAuthenticationMethod.PRE_SHARED_KEYS);
    }
    ikePhase1Proposal.setHashingAlgorithm(
        toIkeAuthenticationAlgorithm(ipsecTunnel.getIkeAuthProtocol()));
    ikePhase1Proposal.setDiffieHellmanGroup(
        toDiffieHellmanGroup(ipsecTunnel.getIkePerfectForwardSecrecy()));
    ikePhase1Proposal.setEncryptionAlgorithm(
        toEncryptionAlgorithm(ipsecTunnel.getIkeEncryptionProtocol()));
    return ikePhase1Proposal;
  }

  @Nonnull
  private static IkePhase1Key toIkePhase1PreSharedKey(
      IpsecTunnel ipsecTunnel, Ip remoteIdentity, String localInterface) {
    IkePhase1Key ikePhase1Key = new IkePhase1Key();
    ikePhase1Key.setKeyType(IkeKeyType.PRE_SHARED_KEY_UNENCRYPTED);
    ikePhase1Key.setKeyHash(ipsecTunnel.getIkePreSharedKeyHash());
    ikePhase1Key.setRemoteIdentity(remoteIdentity.toIpSpace());
    ikePhase1Key.setLocalInterface(localInterface);
    return ikePhase1Key;
  }

  @Nonnull
  private static IkePhase1Policy toIkePhase1Policy(
      String vpnId,
      String ikePhase1ProposalName,
      IkePhase1Key ikePhase1Key,
      Ip remoteIdentity,
      String localInterface) {
    IkePhase1Policy ikePhase1Policy = new IkePhase1Policy(vpnId);
    ikePhase1Policy.setIkePhase1Key(ikePhase1Key);
    ikePhase1Policy.setIkePhase1Proposals(ImmutableList.of(ikePhase1ProposalName));
    ikePhase1Policy.setRemoteIdentity(remoteIdentity.toIpSpace());
    ikePhase1Policy.setLocalInterface(localInterface);
    return ikePhase1Policy;
  }

  @Nonnull
  private static IpsecPhase2Proposal toIpsecPhase2Proposal(
      IpsecTunnel ipsecTunnel, Warnings warnings) {
    IpsecPhase2Proposal ipsecPhase2Proposal = new IpsecPhase2Proposal();
    ipsecPhase2Proposal.setAuthenticationAlgorithm(
        toIpsecAuthenticationAlgorithm(ipsecTunnel.getIpsecAuthProtocol()));
    ipsecPhase2Proposal.setEncryptionAlgorithm(
        toEncryptionAlgorithm(ipsecTunnel.getIpsecEncryptionProtocol()));
    ipsecPhase2Proposal.setProtocols(
        ImmutableSortedSet.of(toIpsecProtocol(ipsecTunnel.getIpsecProtocol())));
    ipsecPhase2Proposal.setIpsecEncapsulationMode(
        toIpsecEncapdulationMode(ipsecTunnel.getIpsecMode(), warnings));
    return ipsecPhase2Proposal;
  }

  @Nonnull
  private static IpsecPhase2Policy toIpsecPhase2Policy(
      IpsecTunnel ipsecTunnel, String ipsecPhase2Proposal) {
    IpsecPhase2Policy ipsecPhase2Policy = new IpsecPhase2Policy();
    ipsecPhase2Policy.setPfsKeyGroup(
        toDiffieHellmanGroup(ipsecTunnel.getIpsecPerfectForwardSecrecy()));
    ipsecPhase2Policy.setProposals(ImmutableList.of(ipsecPhase2Proposal));
    return ipsecPhase2Policy;
  }

  void applyToVpnGateway(AwsConfiguration awsConfiguration, Region region, Warnings warnings) {
    if (!awsConfiguration.getConfigurationNodes().containsKey(_vpnGatewayId)) {
      warnings.redFlag(
          String.format(
              "VPN Gateway \"%s\" referred by VPN connection \"%s\" not found",
              _vpnGatewayId, _vpnConnectionId));
      return;
    }
    Configuration vpnGatewayCfgNode = awsConfiguration.getConfigurationNodes().get(_vpnGatewayId);

    ImmutableSortedMap.Builder<String, IkePhase1Policy> ikePhase1PolicyMapBuilder =
        ImmutableSortedMap.naturalOrder();
    ImmutableSortedMap.Builder<String, IkePhase1Key> ikePhase1KeyMapBuilder =
        ImmutableSortedMap.naturalOrder();
    ImmutableSortedMap.Builder<String, IkePhase1Proposal> ikePhase1ProposalMapBuilder =
        ImmutableSortedMap.naturalOrder();
    ImmutableSortedMap.Builder<String, IpsecPhase2Proposal> ipsecPhase2ProposalMapBuilder =
        ImmutableSortedMap.naturalOrder();
    ImmutableSortedMap.Builder<String, IpsecPhase2Policy> ipsecPhase2PolicyMapBuilder =
        ImmutableSortedMap.naturalOrder();
    ImmutableSortedMap.Builder<String, IpsecPeerConfig> ipsecPeerConfigMapBuilder =
        ImmutableSortedMap.naturalOrder();

    // BGP administrative costs
    int ebgpAdminCost = RoutingProtocol.BGP.getDefaultAdministrativeCost(ConfigurationFormat.AWS);
    int ibgpAdminCost = RoutingProtocol.IBGP.getDefaultAdministrativeCost(ConfigurationFormat.AWS);

    for (int i = 0; i < _ipsecTunnels.size(); i++) {
      int idNum = i + 1;
      String vpnId = _vpnConnectionId + "-" + idNum;
      IpsecTunnel ipsecTunnel = _ipsecTunnels.get(i);
      if (ipsecTunnel.getCgwBgpAsn() != null && (_staticRoutesOnly || !_routes.isEmpty())) {
        throw new BatfishException(
            "Unexpected combination of BGP and static routes for VPN connection: \""
                + _vpnConnectionId
                + "\"");
      }
      // create representation structures and add to configuration node
      String externalInterfaceName = "external" + idNum;
      ConcreteInterfaceAddress externalInterfaceAddress =
          ConcreteInterfaceAddress.create(
              ipsecTunnel.getVgwOutsideAddress(), Prefix.MAX_PREFIX_LENGTH);
      Utils.newInterface(
          externalInterfaceName,
          vpnGatewayCfgNode,
          externalInterfaceAddress,
          "IPSec tunnel " + idNum);

      String vpnInterfaceName = "vpn" + idNum;
      ConcreteInterfaceAddress vpnInterfaceAddress =
          ConcreteInterfaceAddress.create(
              ipsecTunnel.getVgwInsideAddress(), ipsecTunnel.getVgwInsidePrefixLength());
      Utils.newInterface(vpnInterfaceName, vpnGatewayCfgNode, vpnInterfaceAddress, "VPN " + idNum);

      // IPsec data-model
      ikePhase1ProposalMapBuilder.put(vpnId, toIkePhase1Proposal(vpnId, ipsecTunnel));
      IkePhase1Key ikePhase1Key =
          toIkePhase1PreSharedKey(
              ipsecTunnel, ipsecTunnel.getCgwOutsideAddress(), externalInterfaceName);
      ikePhase1KeyMapBuilder.put(vpnId, ikePhase1Key);
      ikePhase1PolicyMapBuilder.put(
          vpnId,
          toIkePhase1Policy(
              vpnId,
              vpnId,
              ikePhase1Key,
              ipsecTunnel.getCgwOutsideAddress(),
              externalInterfaceName));
      ipsecPhase2ProposalMapBuilder.put(vpnId, toIpsecPhase2Proposal(ipsecTunnel, warnings));
      ipsecPhase2PolicyMapBuilder.put(vpnId, toIpsecPhase2Policy(ipsecTunnel, vpnId));
      ipsecPeerConfigMapBuilder.put(
          vpnId,
          IpsecStaticPeerConfig.builder()
              .setTunnelInterface(vpnInterfaceName)
              .setIkePhase1Policy(vpnId)
              .setIpsecPolicy(vpnId)
              .setSourceInterface(externalInterfaceName)
              .setLocalAddress(ipsecTunnel.getVgwOutsideAddress())
              .setDestinationAddress(ipsecTunnel.getCgwOutsideAddress())
              .build());

      // bgp (if configured)
      if (ipsecTunnel.getVgwBgpAsn() != null) {
        BgpProcess proc = vpnGatewayCfgNode.getDefaultVrf().getBgpProcess();
        if (proc == null) {
          proc = new BgpProcess(ipsecTunnel.getVgwInsideAddress(), ebgpAdminCost, ibgpAdminCost);
          proc.setMultipathEquivalentAsPathMatchMode(MultipathEquivalentAsPathMatchMode.EXACT_PATH);
          vpnGatewayCfgNode.getDefaultVrf().setBgpProcess(proc);
        }

        // pre-defined policy names across bgp peers
        String rpRejectAllName = "~REJECT_ALL~";
        String rpAcceptAllEbgpAndSetNextHopSelfName = "~ACCEPT_ALL_EBGP_AND_SET_NEXT_HOP_SELF~";
        String rpAcceptAllName = "~ACCEPT_ALL~";
        String originationPolicyName = vpnId + "_origination";

        // CG peer config
        BgpActivePeerConfig.builder()
            .setPeerAddress(ipsecTunnel.getCgwInsideAddress())
            .setRemoteAs(ipsecTunnel.getCgwBgpAsn())
            .setBgpProcess(proc)
            .setLocalAs(ipsecTunnel.getVgwBgpAsn())
            .setLocalIp(ipsecTunnel.getVgwInsideAddress())
            .setDefaultMetric(BGP_NEIGHBOR_DEFAULT_METRIC)
            .setIpv4UnicastAddressFamily(
                Ipv4UnicastAddressFamily.builder()
                    .setAddressFamilyCapabilities(
                        AddressFamilyCapabilities.builder().setSendCommunity(false).build())
                    .setExportPolicy(originationPolicyName)
                    .build())
            .build();

        VpnGateway vpnGateway = region.getVpnGateways().get(_vpnGatewayId);
        List<String> attachmentVpcIds = vpnGateway.getAttachmentVpcIds();
        if (attachmentVpcIds.size() != 1) {
          throw new BatfishException(
              "Not sure what routes to advertise since VPN Gateway: \""
                  + _vpnGatewayId
                  + "\" for VPN connection: \""
                  + _vpnConnectionId
                  + "\" is linked to multiple VPCs");
        }
        String vpcId = attachmentVpcIds.get(0);

        // iBGP connection to VPC
        Configuration vpcNode = awsConfiguration.getConfigurationNodes().get(vpcId);
        Ip vpcIfaceAddress =
            vpcNode.getAllInterfaces().get(_vpnGatewayId).getConcreteAddress().getIp();
        Ip vgwToVpcIfaceAddress =
            vpnGatewayCfgNode.getAllInterfaces().get(vpcId).getConcreteAddress().getIp();

        // vgw to VPC
        BgpActivePeerConfig.builder()
            .setPeerAddress(vpcIfaceAddress)
            .setRemoteAs(ipsecTunnel.getVgwBgpAsn())
            .setBgpProcess(proc)
            .setLocalAs(ipsecTunnel.getVgwBgpAsn())
            .setLocalIp(vgwToVpcIfaceAddress)
            .setDefaultMetric(BGP_NEIGHBOR_DEFAULT_METRIC)
            .setIpv4UnicastAddressFamily(
                Ipv4UnicastAddressFamily.builder()
                    .setAddressFamilyCapabilities(
                        AddressFamilyCapabilities.builder().setSendCommunity(true).build())
                    .setExportPolicy(rpAcceptAllEbgpAndSetNextHopSelfName)
                    .setImportPolicy(rpRejectAllName)
                    .build())
            .build();

        // iBGP connection from VPC
        BgpProcess vpcProc = new BgpProcess(vpcIfaceAddress, ebgpAdminCost, ibgpAdminCost);
        vpcNode.getDefaultVrf().setBgpProcess(vpcProc);
        vpcProc.setMultipathEquivalentAsPathMatchMode(
            MultipathEquivalentAsPathMatchMode.EXACT_PATH);
        // VPC to vgw
        BgpActivePeerConfig.builder()
            .setPeerAddress(vgwToVpcIfaceAddress)
            .setBgpProcess(vpcProc)
            .setLocalAs(ipsecTunnel.getVgwBgpAsn())
            .setLocalIp(vpcIfaceAddress)
            .setRemoteAs(ipsecTunnel.getVgwBgpAsn())
            .setDefaultMetric(BGP_NEIGHBOR_DEFAULT_METRIC)
            .setIpv4UnicastAddressFamily(
                Ipv4UnicastAddressFamily.builder()
                    .setAddressFamilyCapabilities(
                        AddressFamilyCapabilities.builder().setSendCommunity(true).build())
                    .setImportPolicy(rpAcceptAllName)
                    .setExportPolicy(rpRejectAllName)
                    .build())
            .build();

        // Actually construct all the named policies, put them in the configuration
        If acceptIffEbgp =
            new If(
                new MatchProtocol(RoutingProtocol.BGP),
                ImmutableList.of(Statements.ExitAccept.toStaticStatement()),
                ImmutableList.of(Statements.ExitReject.toStaticStatement()));

        RoutingPolicy vgwRpAcceptAllBgp =
            new RoutingPolicy(rpAcceptAllEbgpAndSetNextHopSelfName, vpnGatewayCfgNode);
        vpnGatewayCfgNode.getRoutingPolicies().put(vgwRpAcceptAllBgp.getName(), vgwRpAcceptAllBgp);
        vgwRpAcceptAllBgp.setStatements(
            ImmutableList.of(new SetNextHop(SelfNextHop.getInstance(), false), acceptIffEbgp));
        RoutingPolicy vgwRpRejectAll = new RoutingPolicy(rpRejectAllName, vpnGatewayCfgNode);
        vpnGatewayCfgNode.getRoutingPolicies().put(rpRejectAllName, vgwRpRejectAll);

        RoutingPolicy vpcRpAcceptAll = new RoutingPolicy(rpAcceptAllName, vpcNode);
        vpcNode.getRoutingPolicies().put(rpAcceptAllName, vpcRpAcceptAll);
        vpcRpAcceptAll.setStatements(ImmutableList.of(Statements.ExitAccept.toStaticStatement()));
        RoutingPolicy vpcRpRejectAll = new RoutingPolicy(rpRejectAllName, vpcNode);
        vpcNode.getRoutingPolicies().put(rpRejectAllName, vpcRpRejectAll);

        Vpc vpc = region.getVpcs().get(vpcId);
        RoutingPolicy originationRoutingPolicy =
            new RoutingPolicy(originationPolicyName, vpnGatewayCfgNode);
        vpnGatewayCfgNode.getRoutingPolicies().put(originationPolicyName, originationRoutingPolicy);
        If originationIf = new If();
        List<Statement> statements = originationRoutingPolicy.getStatements();
        statements.add(originationIf);
        statements.add(Statements.ExitReject.toStaticStatement());
        originationIf
            .getTrueStatements()
            .add(new SetOrigin(new LiteralOrigin(OriginType.IGP, null)));
        originationIf.getTrueStatements().add(Statements.ExitAccept.toStaticStatement());
        RouteFilterList originationRouteFilter = new RouteFilterList(originationPolicyName);
        vpnGatewayCfgNode.getRouteFilterLists().put(originationPolicyName, originationRouteFilter);
        vpc.getCidrBlockAssociations()
            .forEach(
                prefix -> {
                  RouteFilterLine matchOutgoingPrefix =
                      new RouteFilterLine(
                          LineAction.PERMIT,
                          prefix,
                          new SubRange(prefix.getPrefixLength(), prefix.getPrefixLength()));
                  originationRouteFilter.addLine(matchOutgoingPrefix);
                });
        Conjunction conj = new Conjunction();
        originationIf.setGuard(conj);
        conj.getConjuncts().add(new MatchProtocol(RoutingProtocol.STATIC));
        conj.getConjuncts()
            .add(
                new MatchPrefixSet(
                    DestinationNetwork.instance(), new NamedPrefixSet(originationPolicyName)));
      }

      // static routes (if configured)
      for (Prefix staticRoutePrefix : _routes) {
        StaticRoute staticRoute =
            StaticRoute.builder()
                .setNetwork(staticRoutePrefix)
                .setNextHopIp(ipsecTunnel.getCgwInsideAddress())
                .setAdministrativeCost(Route.DEFAULT_STATIC_ROUTE_ADMIN)
                .setMetric(Route.DEFAULT_STATIC_ROUTE_COST)
                .build();

        vpnGatewayCfgNode.getDefaultVrf().getStaticRoutes().add(staticRoute);
      }
    }
    vpnGatewayCfgNode.setIkePhase1Proposals(ikePhase1ProposalMapBuilder.build());
    vpnGatewayCfgNode.setIkePhase1Keys(ikePhase1KeyMapBuilder.build());
    vpnGatewayCfgNode.setIkePhase1Policies(ikePhase1PolicyMapBuilder.build());
    vpnGatewayCfgNode.setIpsecPhase2Proposals(ipsecPhase2ProposalMapBuilder.build());
    vpnGatewayCfgNode.setIpsecPhase2Policies(ipsecPhase2PolicyMapBuilder.build());
    vpnGatewayCfgNode.setIpsecPeerConfigs(ipsecPeerConfigMapBuilder.build());
  }

  @Nonnull
  String getCustomerGatewayId() {
    return _customerGatewayId;
  }

  @Override
  public String getId() {
    return _vpnConnectionId;
  }

  @Nonnull
  List<IpsecTunnel> getIpsecTunnels() {
    return _ipsecTunnels;
  }

  @Nonnull
  List<Prefix> getRoutes() {
    return _routes;
  }

  boolean getStaticRoutesOnly() {
    return _staticRoutesOnly;
  }

  @Nonnull
  List<VgwTelemetry> getVgwTelemetrys() {
    return _vgwTelemetrys;
  }

  @Nonnull
  String getVpnConnectionId() {
    return _vpnConnectionId;
  }

  @Nonnull
  String getVpnGatewayId() {
    return _vpnGatewayId;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof VpnConnection)) {
      return false;
    }
    VpnConnection that = (VpnConnection) o;
    return _staticRoutesOnly == that._staticRoutesOnly
        && Objects.equals(_customerGatewayId, that._customerGatewayId)
        && Objects.equals(_ipsecTunnels, that._ipsecTunnels)
        && Objects.equals(_routes, that._routes)
        && Objects.equals(_vgwTelemetrys, that._vgwTelemetrys)
        && Objects.equals(_vpnConnectionId, that._vpnConnectionId)
        && Objects.equals(_vpnGatewayId, that._vpnGatewayId);
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        _customerGatewayId,
        _ipsecTunnels,
        _routes,
        _staticRoutesOnly,
        _vgwTelemetrys,
        _vpnConnectionId,
        _vpnGatewayId);
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("_customerGatewayId", _customerGatewayId)
        .add("_ipsecTunnels", _ipsecTunnels)
        .add("_routes", _routes)
        .add("_staticRoutesOnly", _staticRoutesOnly)
        .add("_vgwTelemetrys", _vgwTelemetrys)
        .add("_vpnConnectionId", _vpnConnectionId)
        .add("_vpnGatewayId", _vpnGatewayId)
        .toString();
  }
}
