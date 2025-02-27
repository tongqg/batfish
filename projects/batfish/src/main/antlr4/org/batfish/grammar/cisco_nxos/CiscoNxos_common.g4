parser grammar CiscoNxos_common;

options {
  tokenVocab = CiscoNxosLexer;
}

bgp_asn
:
  large = uint32
  | high = uint16 PERIOD low = uint16
;

cisco_nxos_password
:
// Lexer mode change required prior to entering this rule
  PASSWORD_0? text = PASSWORD_0_TEXT
  | PASSWORD_3
  (
    text = PASSWORD_3_TEXT
    | malformed = PASSWORD_3_MALFORMED_TEXT
  )
  | PASSWORD_7
  (
    PASSWORD_7_TEXT
    | malformed = PASSWORD_7_MALFORMED_TEXT
  )
;

class_map_name
:
// 1-40 characters
  WORD
;

// Shared NX-OS syntax for route-target, redistribution, route leak, etc.
both_export_import
:
  BOTH
  | EXPORT
  | IMPORT
;

double_quoted_string
:
  DOUBLE_QUOTE text = quoted_text? DOUBLE_QUOTE
;

group_name
:
// 1-32 characters
  WORD
;

interface_address
:
  address = ip_address mask = subnet_mask
  | iaddress = ip_prefix
;

interface_name
:
  prefix = interface_prefix middle = interface_middle? parent_suffix =
  interface_parent_suffix? first = uint16
;

interface_prefix
:
  ETHERNET
  | LOOPBACK
  | MGMT
  | PORT_CHANNEL
  | VLAN
;

interface_middle
:
  (
    uint8 FORWARD_SLASH
  )+
;

interface_parent_suffix
:
  num = uint16 period = PERIOD
;

ip_access_list_name
:
// 1-64 characters
  WORD
;

ip_address
:
  IP_ADDRESS
  | SUBNET_MASK
;

ip_as_path_access_list_name
:
// 1-63 characters
  WORD
;

ip_community_list_name
:
// 1-63 characters
  WORD
;

ip_prefix
:
  IP_PREFIX
;

ip_prefix_list_name
:
// 1-63 chars
  WORD
;

ipv6_address
:
  IPV6_ADDRESS
;

ipv6_prefix
:
  IPV6_PREFIX
;

line_action
:
  deny = DENY
  | permit = PERMIT
;

literal_standard_community
:
  high = uint16 COLON low = uint16
;

null_rest_of_line
:
  ~NEWLINE* NEWLINE
;

nve_interface_name
:
  NVE first = uint8
;

ospf_area_id
:
  num = uint32
  | ip = ip_address
;

policy_map_name
:
// 1-40 characters
  WORD
;

quoted_text
:
  QUOTED_TEXT
;

route_distinguisher
:
// The order of these rules is significant: 1:1 should be parsed as type 0, not type 2.
// That string matches both rules with the same number of tokens; ANTLR4 will prefer the first one.
  hi0 = uint16 COLON lo0 = uint32
  | hi1 = ip_address COLON lo1 = uint16
  | hi2 = uint32 COLON lo2 = uint16
;

route_distinguisher_or_auto
:
  AUTO
  | route_distinguisher
;

route_map_name
:
// 1-63 chars
  WORD
;

route_network
:
  address = ip_address mask = subnet_mask
  | prefix = ip_prefix
;

router_ospf_name
:
// 1-20 characters
  WORD
;

standard_community
:
  literal = literal_standard_community
  | INTERNET
  | LOCAL_AS
  | NO_ADVERTISE
  | NO_EXPORT
;

subdomain_name
:
  SUBDOMAIN_NAME
;

subnet_mask
:
  SUBNET_MASK
;

template_name
:
// 1-80 chars
  WORD
;

track_object_number
:
// 1-512
  uint16
;

uint8
:
  UINT8
;

uint16
:
  UINT8
  | UINT16
;

uint32
:
  UINT8
  | UINT16
  | UINT32
;

user_name
:
// 1-32 characters
  WORD
;

user_password
:
  WORD
// 1-127 characters
;

vlan_id
:
// 1-4094
  UINT8
  | UINT16
;

vlan_id_range
:
  intervals += vlan_id_interval
  (
    COMMA intervals += vlan_id_interval
  )*
;

vlan_id_interval
:
  low = vlan_id
  (
    DASH high = vlan_id
  )?
;

vni_number
:
// 0-16777214
  uint32
;

vrf_name
:
  WORD
;
