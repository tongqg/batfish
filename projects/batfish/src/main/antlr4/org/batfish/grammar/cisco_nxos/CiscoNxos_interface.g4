parser grammar CiscoNxos_interface;

import CiscoNxos_common;

options {
  tokenVocab = CiscoNxosLexer;
}

s_interface
:
  INTERFACE
  (
      s_interface_nve
      | s_interface_regular
  )
;

s_interface_nve
:
  nverange = nve_interface_range NEWLINE
  (
    nve_global
    | nve_host_reachability
    | nve_member
    | nve_no
    | nve_source_interface
  )*
;

s_interface_regular
:
  irange = interface_range NEWLINE
  (
    i_bandwidth
    | i_channel_group
    | i_description
    | i_encapsulation
    | i_hsrp
    | i_ip
    | i_mtu
    | i_no
    | i_null
    | i_shutdown
    | i_speed
    | i_switchport
    | i_vrf_member
  )*
;

i_bandwidth
:
  BANDWIDTH bw = interface_bandwidth_kbps NEWLINE
;

interface_bandwidth_kbps
:
// 1-100000000, units are kbps (for effective range of 1kbps-100Gbps)
  UINT8
  | UINT16
  | UINT32
;

i_channel_group
:
  CHANNEL_GROUP id = channel_id force = FORCE? (MODE (ACTIVE|ON|PASSIVE))? NEWLINE
;

channel_id
:
// 1-4096
  UINT8
  | UINT16
;

i_description
:
  DESCRIPTION desc = interface_description NEWLINE
;

interface_description
:
  REMARK_TEXT
;

i_encapsulation
:
  ENCAPSULATION DOT1Q vlan = vlan_id NEWLINE
;

i_hsrp
:
  HSRP
  (
    ih_delay
    | ih_group
    | ih_version
  )
;

ih_delay
:
  DELAY ihd_reload
;

ihd_reload
:
  RELOAD delay_s = hsrp_delay_reload NEWLINE
;

hsrp_delay_reload
:
// 0-10000 (s)
  uint16
;

ih_group
:
  group = hsrp_group_number NEWLINE
  (
    ihg_ip
    | ihg_preempt
    | ihg_priority
    | ihg_timers
    | ihg_track
  )*
;

hsrp_group_number
:
// 0-4095
  uint16
;

ihg_ip
:
  IP
  (
    ip = ip_address
    | prefix = ip_prefix
  ) NEWLINE
;

ihg_preempt
:
  PREEMPT
  (
    DELAY
    (
      MINIMUM minimum_s = hsrp_preempt_delay
      | RELOAD reload_s = hsrp_preempt_delay
      | SYNC sync_s = hsrp_preempt_delay
    )*
  )? NEWLINE
;

hsrp_preempt_delay
:
// 0-3600 (s)
  uint16
;

ihg_priority
:
  PRIORITY priority = uint8 NEWLINE
;

ihg_timers
:
  TIMERS
  (
    hello_interval_s = hsrp_timers_hello_interval_s
    | MSEC hello_interval_ms = hsrp_timers_hello_interval_ms
  )
  (
    hold_time_s = hsrp_timers_hold_time_s
    | MSEC hold_time_ms = hsrp_timers_hold_time_ms
  ) NEWLINE
;

hsrp_timers_hello_interval_s
:
// 1-254 (s)
  uint8
;

hsrp_timers_hello_interval_ms
:
// 250-999 (ms)
  uint16
;

hsrp_timers_hold_time_s
:
// 3-255 (s)
  uint8
;

hsrp_timers_hold_time_ms
:
// 750-3000 (ms)
  uint16
;

ihg_track
:
  TRACK num = track_object_number (DECREMENT decrement = hsrp_track_decrement)? NEWLINE
;

hsrp_track_decrement
:
// 1-255
  uint8
;

ih_version
:
  VERSION version = hsrp_version NEWLINE
;

hsrp_version
:
  HSRP_VERSION_1
  | HSRP_VERSION_2  
;

i_ip
:
  IP
  (
    i_ip_address
    | i_ip_null
    | i_ip_ospf
    | i_ip_router
  )
;

i_ip_address
:
  ADDRESS addr = interface_address SECONDARY? (TAG tag = uint32)? NEWLINE
;

i_ip_null
:
  (
    IGMP
    | PIM
    | REDIRECTS
  ) null_rest_of_line
;

i_ip_ospf
:
  OSPF
  (
    iipo_dead_interval
    | iipo_hello_interval
    | iipo_message_digest_key
    | iipo_network
  )
;

iipo_dead_interval
:
  DEAD_INTERVAL interval_s = ospf_dead_interval NEWLINE
;

ospf_dead_interval
:
// 1-65535
  uint16
;

iipo_hello_interval
:
  HELLO_INTERVAL interval_s = ospf_hello_interval NEWLINE
;

ospf_hello_interval
:
// 1-65535
  uint16
;

iipo_message_digest_key
:
  MESSAGE_DIGEST_KEY null_rest_of_line
;

iipo_network
:
  NETWORK
  (
    BROADCAST
    | POINT_TO_POINT
  ) NEWLINE
;

i_ip_router
:
  ROUTER iipr_ospf
;

iipr_ospf
:
  OSPF name = router_ospf_name AREA area = ospf_area_id NEWLINE
;

i_mtu
:
  MTU interface_mtu NEWLINE
;

interface_mtu
:
// range depends on interface type
  mtu = uint16
;

i_no
:
  NO
  (
    i_no_autostate
    | i_no_bfd
    | i_no_null
    | i_no_shutdown
    | i_no_switchport
  )
;

i_no_autostate
:
  AUTOSTATE NEWLINE
;

i_no_bfd
:
  BFD ECHO NEWLINE
;

i_no_shutdown
:
  SHUTDOWN NEWLINE
;

i_no_switchport
:
  SWITCHPORT NEWLINE
;

i_no_null
:
  (
    IP
    | NEGOTIATE
  ) null_rest_of_line
;

i_null
:
  (
    DUPLEX
    | LACP
    | SPANNING_TREE
    | STORM_CONTROL
  ) null_rest_of_line
;

i_shutdown
:
  SHUTDOWN NEWLINE
;

i_speed
:
  SPEED speed = interface_speed NEWLINE
;

interface_speed
:
// 100; 1,000; 10,000; 25,000; 40,000; 100,000
  uint32
;

i_switchport
:
  SWITCHPORT
  (
    i_switchport_access
    | i_switchport_mode
    | i_switchport_trunk_allowed
    | i_switchport_trunk
  )
;

i_switchport_access
:
  ACCESS VLAN vlan = vlan_id NEWLINE
;

i_switchport_mode
:
  MODE
  (
    i_switchport_mode_access
    | i_switchport_mode_dot1q_tunnel
    | i_switchport_mode_fex_fabric
    | i_switchport_mode_trunk
  )
;

i_switchport_mode_access
:
  ACCESS NEWLINE
;

i_switchport_mode_dot1q_tunnel
:
  DOT1Q_TUNNEL NEWLINE
;

i_switchport_mode_fex_fabric
:
  FEX_FABRIC NEWLINE
;

i_switchport_mode_trunk
:
  TRUNK NEWLINE
;

i_switchport_trunk
:
  TRUNK
  (
    i_switchport_trunk_allowed
    | i_switchport_trunk_native
  )
;

i_switchport_trunk_allowed
:
  ALLOWED VLAN
  (
    (
      ADD
      | REMOVE
    )? vlans = vlan_id_range
    | EXCEPT except = vlan_id
    | NONE
  ) NEWLINE
;

i_switchport_trunk_native
:
  NATIVE VLAN vlan = vlan_id NEWLINE
;

i_vrf_member
:
  VRF MEMBER name = vrf_name NEWLINE
;

interface_range
:
  iname = interface_name
  (
    DASH last = uint16
  )?
;

nve_interface_range
:
  iname = nve_interface_name
  (
    DASH last = uint8
  )?
;

nve_global
:
  GLOBAL
  (
    nvg_ingress_replication
    | nvg_mcast_group
    | nvg_suppress_arp
  )
;

nvg_ingress_replication
:
  INGRESS_REPLICATION PROTOCOL BGP NEWLINE
;

nvg_mcast_group
:
  MCAST_GROUP ip_address (L2 | L3) NEWLINE
;

nvg_suppress_arp
:
  SUPPRESS_ARP NEWLINE
;

nve_host_reachability
:
  HOST_REACHABILITY PROTOCOL BGP NEWLINE
;

nve_member
:
// 1-16777214
  MEMBER VNI vni = vni_number ASSOCIATE_VRF? NEWLINE
  (
     nvm_ingress_replication
     | nvm_mcast_group
     | nvm_peer_ip
     | nvm_peer_vtep
     | nvm_spine_anycast_gateway
     | nvm_suppress_arp
  )*
;

nvm_ingress_replication
:
   INGRESS_REPLICATION PROTOCOL (BGP | STATIC) NEWLINE
;

nvm_mcast_group
:
   MCAST_GROUP first = ip_address second = ip_address? NEWLINE
;

nvm_peer_ip
:
   PEER_IP ip_address NEWLINE
;

nvm_peer_vtep
:
   PEER_VTEP ip_address NEWLINE
;

nvm_spine_anycast_gateway
:
   SPINE_ANYCAST_GATEWAY NEWLINE
;

nvm_suppress_arp
:
   SUPPRESS_ARP DISABLE? NEWLINE
;

nve_no
:
   NO nve_no_shutdown
;

nve_no_shutdown
:
   SHUTDOWN NEWLINE
;

nve_source_interface
:
   SOURCE_INTERFACE name = interface_name NEWLINE
;
