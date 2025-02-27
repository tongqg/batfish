package org.batfish.representation.cisco_nxos;

/** A visitor of {@link IpCommunityList}. */
public interface IpCommunityListVisitor<T> {

  T visitIpCommunityListStandard(IpCommunityListStandard ipCommunityListStandard);
}
