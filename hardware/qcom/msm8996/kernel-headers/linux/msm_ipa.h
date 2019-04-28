/****************************************************************************
 ****************************************************************************
 ***
 ***   This header was automatically generated from a Linux kernel header
 ***   of the same name, to make information necessary for userspace to
 ***   call into the kernel available to libc.  It contains only constants,
 ***   structures, and macros generated from the original header, and thus,
 ***   contains no copyrightable information.
 ***
 ***   To edit the content of this header, modify the corresponding
 ***   source file (e.g. under external/kernel-headers/original/) then
 ***   run bionic/libc/kernel/tools/update_all.py
 ***
 ***   Any manual change here will be lost the next time this script will
 ***   be run. You've been warned!
 ***
 ****************************************************************************
 ****************************************************************************/
#ifndef _UAPI_MSM_IPA_H_
#define _UAPI_MSM_IPA_H_
#include <stdint.h>
#include <stddef.h>
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
#include <sys/stat.h>
#include <linux/ioctl.h>
#include <linux/types.h>
#include <linux/if_ether.h>
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
#define IPA_IOC_MAGIC 0xCF
#define IPA_DFLT_RT_TBL_NAME "ipa_dflt_rt"
#define IPA_IOCTL_ADD_HDR 0
#define IPA_IOCTL_DEL_HDR 1
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
#define IPA_IOCTL_ADD_RT_RULE 2
#define IPA_IOCTL_DEL_RT_RULE 3
#define IPA_IOCTL_ADD_FLT_RULE 4
#define IPA_IOCTL_DEL_FLT_RULE 5
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
#define IPA_IOCTL_COMMIT_HDR 6
#define IPA_IOCTL_RESET_HDR 7
#define IPA_IOCTL_COMMIT_RT 8
#define IPA_IOCTL_RESET_RT 9
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
#define IPA_IOCTL_COMMIT_FLT 10
#define IPA_IOCTL_RESET_FLT 11
#define IPA_IOCTL_DUMP 12
#define IPA_IOCTL_GET_RT_TBL 13
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
#define IPA_IOCTL_PUT_RT_TBL 14
#define IPA_IOCTL_COPY_HDR 15
#define IPA_IOCTL_QUERY_INTF 16
#define IPA_IOCTL_QUERY_INTF_TX_PROPS 17
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
#define IPA_IOCTL_QUERY_INTF_RX_PROPS 18
#define IPA_IOCTL_GET_HDR 19
#define IPA_IOCTL_PUT_HDR 20
#define IPA_IOCTL_SET_FLT 21
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
#define IPA_IOCTL_ALLOC_NAT_MEM 22
#define IPA_IOCTL_V4_INIT_NAT 23
#define IPA_IOCTL_NAT_DMA 24
#define IPA_IOCTL_V4_DEL_NAT 26
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
#define IPA_IOCTL_PULL_MSG 27
#define IPA_IOCTL_GET_NAT_OFFSET 28
#define IPA_IOCTL_RM_ADD_DEPENDENCY 29
#define IPA_IOCTL_RM_DEL_DEPENDENCY 30
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
#define IPA_IOCTL_GENERATE_FLT_EQ 31
#define IPA_IOCTL_QUERY_INTF_EXT_PROPS 32
#define IPA_IOCTL_QUERY_EP_MAPPING 33
#define IPA_IOCTL_QUERY_RT_TBL_INDEX 34
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
#define IPA_IOCTL_WRITE_QMAPID 35
#define IPA_IOCTL_MDFY_FLT_RULE 36
#define IPA_IOCTL_NOTIFY_WAN_UPSTREAM_ROUTE_ADD 37
#define IPA_IOCTL_NOTIFY_WAN_UPSTREAM_ROUTE_DEL 38
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
#define IPA_IOCTL_NOTIFY_WAN_EMBMS_CONNECTED 39
#define IPA_IOCTL_ADD_HDR_PROC_CTX 40
#define IPA_IOCTL_DEL_HDR_PROC_CTX 41
#define IPA_IOCTL_MDFY_RT_RULE 42
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
#define IPA_IOCTL_ADD_RT_RULE_AFTER 43
#define IPA_IOCTL_ADD_FLT_RULE_AFTER 44
#define IPA_IOCTL_GET_HW_VERSION 45
#define IPA_IOCTL_MAX 46
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
#define IPA_HDR_MAX_SIZE 64
#define IPA_RESOURCE_NAME_MAX 32
#define IPA_NUM_PROPS_MAX 35
#define IPA_MAC_ADDR_SIZE 6
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
#define IPA_MBIM_MAX_STREAM_NUM 8
#define IPA_FLT_TOS (1ul << 0)
#define IPA_FLT_PROTOCOL (1ul << 1)
#define IPA_FLT_SRC_ADDR (1ul << 2)
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
#define IPA_FLT_DST_ADDR (1ul << 3)
#define IPA_FLT_SRC_PORT_RANGE (1ul << 4)
#define IPA_FLT_DST_PORT_RANGE (1ul << 5)
#define IPA_FLT_TYPE (1ul << 6)
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
#define IPA_FLT_CODE (1ul << 7)
#define IPA_FLT_SPI (1ul << 8)
#define IPA_FLT_SRC_PORT (1ul << 9)
#define IPA_FLT_DST_PORT (1ul << 10)
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
#define IPA_FLT_TC (1ul << 11)
#define IPA_FLT_FLOW_LABEL (1ul << 12)
#define IPA_FLT_NEXT_HDR (1ul << 13)
#define IPA_FLT_META_DATA (1ul << 14)
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
#define IPA_FLT_FRAGMENT (1ul << 15)
#define IPA_FLT_TOS_MASKED (1ul << 16)
#define IPA_FLT_MAC_SRC_ADDR_ETHER_II (1ul << 17)
#define IPA_FLT_MAC_DST_ADDR_ETHER_II (1ul << 18)
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
#define IPA_FLT_MAC_SRC_ADDR_802_3 (1ul << 19)
#define IPA_FLT_MAC_DST_ADDR_802_3 (1ul << 20)
#define IPA_FLT_MAC_ETHER_TYPE (1ul << 21)
enum ipa_client_type {
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
  IPA_CLIENT_PROD,
  IPA_CLIENT_HSIC1_PROD = IPA_CLIENT_PROD,
  IPA_CLIENT_WLAN1_PROD,
  IPA_CLIENT_HSIC2_PROD,
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
  IPA_CLIENT_USB2_PROD,
  IPA_CLIENT_HSIC3_PROD,
  IPA_CLIENT_USB3_PROD,
  IPA_CLIENT_HSIC4_PROD,
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
  IPA_CLIENT_USB4_PROD,
  IPA_CLIENT_HSIC5_PROD,
  IPA_CLIENT_USB_PROD,
  IPA_CLIENT_A5_WLAN_AMPDU_PROD,
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
  IPA_CLIENT_A2_EMBEDDED_PROD,
  IPA_CLIENT_A2_TETHERED_PROD,
  IPA_CLIENT_APPS_LAN_WAN_PROD,
  IPA_CLIENT_APPS_CMD_PROD,
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
  IPA_CLIENT_ODU_PROD,
  IPA_CLIENT_MHI_PROD,
  IPA_CLIENT_Q6_LAN_PROD,
  IPA_CLIENT_Q6_WAN_PROD,
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
  IPA_CLIENT_Q6_CMD_PROD,
  IPA_CLIENT_MEMCPY_DMA_SYNC_PROD,
  IPA_CLIENT_MEMCPY_DMA_ASYNC_PROD,
  IPA_CLIENT_Q6_DECOMP_PROD,
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
  IPA_CLIENT_Q6_DECOMP2_PROD,
  IPA_CLIENT_UC_USB_PROD,
  IPA_CLIENT_TEST_PROD,
  IPA_CLIENT_TEST1_PROD,
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
  IPA_CLIENT_TEST2_PROD,
  IPA_CLIENT_TEST3_PROD,
  IPA_CLIENT_TEST4_PROD,
  IPA_CLIENT_CONS,
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
  IPA_CLIENT_HSIC1_CONS = IPA_CLIENT_CONS,
  IPA_CLIENT_WLAN1_CONS,
  IPA_CLIENT_HSIC2_CONS,
  IPA_CLIENT_USB2_CONS,
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
  IPA_CLIENT_WLAN2_CONS,
  IPA_CLIENT_HSIC3_CONS,
  IPA_CLIENT_USB3_CONS,
  IPA_CLIENT_WLAN3_CONS,
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
  IPA_CLIENT_HSIC4_CONS,
  IPA_CLIENT_USB4_CONS,
  IPA_CLIENT_WLAN4_CONS,
  IPA_CLIENT_HSIC5_CONS,
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
  IPA_CLIENT_USB_CONS,
  IPA_CLIENT_USB_DPL_CONS,
  IPA_CLIENT_A2_EMBEDDED_CONS,
  IPA_CLIENT_A2_TETHERED_CONS,
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
  IPA_CLIENT_A5_LAN_WAN_CONS,
  IPA_CLIENT_APPS_LAN_CONS,
  IPA_CLIENT_APPS_WAN_CONS,
  IPA_CLIENT_ODU_EMB_CONS,
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
  IPA_CLIENT_ODU_TETH_CONS,
  IPA_CLIENT_MHI_CONS,
  IPA_CLIENT_Q6_LAN_CONS,
  IPA_CLIENT_Q6_WAN_CONS,
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
  IPA_CLIENT_Q6_DUN_CONS,
  IPA_CLIENT_MEMCPY_DMA_SYNC_CONS,
  IPA_CLIENT_MEMCPY_DMA_ASYNC_CONS,
  IPA_CLIENT_Q6_DECOMP_CONS,
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
  IPA_CLIENT_Q6_DECOMP2_CONS,
  IPA_CLIENT_Q6_LTE_WIFI_AGGR_CONS,
  IPA_CLIENT_TEST_CONS,
  IPA_CLIENT_TEST1_CONS,
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
  IPA_CLIENT_TEST2_CONS,
  IPA_CLIENT_TEST3_CONS,
  IPA_CLIENT_TEST4_CONS,
  IPA_CLIENT_MAX,
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
};
#define IPA_CLIENT_IS_APPS_CONS(client) ((client) == IPA_CLIENT_APPS_LAN_CONS || (client) == IPA_CLIENT_APPS_WAN_CONS)
#define IPA_CLIENT_IS_USB_CONS(client) ((client) == IPA_CLIENT_USB_CONS || (client) == IPA_CLIENT_USB2_CONS || (client) == IPA_CLIENT_USB3_CONS || (client) == IPA_CLIENT_USB_DPL_CONS || (client) == IPA_CLIENT_USB4_CONS)
#define IPA_CLIENT_IS_WLAN_CONS(client) ((client) == IPA_CLIENT_WLAN1_CONS || (client) == IPA_CLIENT_WLAN2_CONS || (client) == IPA_CLIENT_WLAN3_CONS || (client) == IPA_CLIENT_WLAN4_CONS)
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
#define IPA_CLIENT_IS_ODU_CONS(client) ((client) == IPA_CLIENT_ODU_EMB_CONS || (client) == IPA_CLIENT_ODU_TETH_CONS)
#define IPA_CLIENT_IS_Q6_CONS(client) ((client) == IPA_CLIENT_Q6_LAN_CONS || (client) == IPA_CLIENT_Q6_WAN_CONS || (client) == IPA_CLIENT_Q6_DUN_CONS || (client) == IPA_CLIENT_Q6_DECOMP_CONS || (client) == IPA_CLIENT_Q6_DECOMP2_CONS || (client) == IPA_CLIENT_Q6_LTE_WIFI_AGGR_CONS)
#define IPA_CLIENT_IS_Q6_PROD(client) ((client) == IPA_CLIENT_Q6_LAN_PROD || (client) == IPA_CLIENT_Q6_WAN_PROD || (client) == IPA_CLIENT_Q6_CMD_PROD || (client) == IPA_CLIENT_Q6_DECOMP_PROD || (client) == IPA_CLIENT_Q6_DECOMP2_PROD)
#define IPA_CLIENT_IS_Q6_NON_ZIP_CONS(client) ((client) == IPA_CLIENT_Q6_LAN_CONS || (client) == IPA_CLIENT_Q6_WAN_CONS || (client) == IPA_CLIENT_Q6_DUN_CONS || (client) == IPA_CLIENT_Q6_LTE_WIFI_AGGR_CONS)
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
#define IPA_CLIENT_IS_Q6_ZIP_CONS(client) ((client) == IPA_CLIENT_Q6_DECOMP_CONS || (client) == IPA_CLIENT_Q6_DECOMP2_CONS)
#define IPA_CLIENT_IS_Q6_NON_ZIP_PROD(client) ((client) == IPA_CLIENT_Q6_LAN_PROD || (client) == IPA_CLIENT_Q6_WAN_PROD || (client) == IPA_CLIENT_Q6_CMD_PROD)
#define IPA_CLIENT_IS_Q6_ZIP_PROD(client) ((client) == IPA_CLIENT_Q6_DECOMP_PROD || (client) == IPA_CLIENT_Q6_DECOMP2_PROD)
#define IPA_CLIENT_IS_MEMCPY_DMA_CONS(client) ((client) == IPA_CLIENT_MEMCPY_DMA_SYNC_CONS || (client) == IPA_CLIENT_MEMCPY_DMA_ASYNC_CONS)
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
#define IPA_CLIENT_IS_MEMCPY_DMA_PROD(client) ((client) == IPA_CLIENT_MEMCPY_DMA_SYNC_PROD || (client) == IPA_CLIENT_MEMCPY_DMA_ASYNC_PROD)
#define IPA_CLIENT_IS_MHI_CONS(client) ((client) == IPA_CLIENT_MHI_CONS)
#define IPA_CLIENT_IS_MHI(client) ((client) == IPA_CLIENT_MHI_CONS || (client) == IPA_CLIENT_MHI_PROD)
#define IPA_CLIENT_IS_TEST_PROD(client) ((client) == IPA_CLIENT_TEST_PROD || (client) == IPA_CLIENT_TEST1_PROD || (client) == IPA_CLIENT_TEST2_PROD || (client) == IPA_CLIENT_TEST3_PROD || (client) == IPA_CLIENT_TEST4_PROD)
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
#define IPA_CLIENT_IS_TEST_CONS(client) ((client) == IPA_CLIENT_TEST_CONS || (client) == IPA_CLIENT_TEST1_CONS || (client) == IPA_CLIENT_TEST2_CONS || (client) == IPA_CLIENT_TEST3_CONS || (client) == IPA_CLIENT_TEST4_CONS)
#define IPA_CLIENT_IS_TEST(client) (IPA_CLIENT_IS_TEST_PROD(client) || IPA_CLIENT_IS_TEST_CONS(client))
enum ipa_ip_type {
  IPA_IP_v4,
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
  IPA_IP_v6,
  IPA_IP_MAX
};
enum ipa_rule_type {
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
  IPA_RULE_HASHABLE,
  IPA_RULE_NON_HASHABLE,
  IPA_RULE_TYPE_MAX
};
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
enum ipa_flt_action {
  IPA_PASS_TO_ROUTING,
  IPA_PASS_TO_SRC_NAT,
  IPA_PASS_TO_DST_NAT,
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
  IPA_PASS_TO_EXCEPTION
};
enum ipa_wlan_event {
  WLAN_CLIENT_CONNECT,
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
  WLAN_CLIENT_DISCONNECT,
  WLAN_CLIENT_POWER_SAVE_MODE,
  WLAN_CLIENT_NORMAL_MODE,
  SW_ROUTING_ENABLE,
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
  SW_ROUTING_DISABLE,
  WLAN_AP_CONNECT,
  WLAN_AP_DISCONNECT,
  WLAN_STA_CONNECT,
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
  WLAN_STA_DISCONNECT,
  WLAN_CLIENT_CONNECT_EX,
  WLAN_SWITCH_TO_SCC,
  WLAN_SWITCH_TO_MCC,
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
  WLAN_WDI_ENABLE,
  WLAN_WDI_DISABLE,
  IPA_WLAN_EVENT_MAX
};
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
enum ipa_wan_event {
  WAN_UPSTREAM_ROUTE_ADD = IPA_WLAN_EVENT_MAX,
  WAN_UPSTREAM_ROUTE_DEL,
  WAN_EMBMS_CONNECT,
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
  WAN_XLAT_CONNECT,
  IPA_WAN_EVENT_MAX
};
enum ipa_ecm_event {
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
  ECM_CONNECT = IPA_WAN_EVENT_MAX,
  ECM_DISCONNECT,
  IPA_ECM_EVENT_MAX,
};
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
enum ipa_tethering_stats_event {
  IPA_TETHERING_STATS_UPDATE_STATS = IPA_ECM_EVENT_MAX,
  IPA_TETHERING_STATS_UPDATE_NETWORK_STATS,
  IPA_TETHERING_STATS_EVENT_MAX,
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
  IPA_EVENT_MAX_NUM = IPA_TETHERING_STATS_EVENT_MAX
};
#define IPA_EVENT_MAX ((int) IPA_EVENT_MAX_NUM)
enum ipa_rm_resource_name {
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
  IPA_RM_RESOURCE_PROD = 0,
  IPA_RM_RESOURCE_Q6_PROD = IPA_RM_RESOURCE_PROD,
  IPA_RM_RESOURCE_USB_PROD,
  IPA_RM_RESOURCE_USB_DPL_DUMMY_PROD,
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
  IPA_RM_RESOURCE_HSIC_PROD,
  IPA_RM_RESOURCE_STD_ECM_PROD,
  IPA_RM_RESOURCE_RNDIS_PROD,
  IPA_RM_RESOURCE_WWAN_0_PROD,
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
  IPA_RM_RESOURCE_WLAN_PROD,
  IPA_RM_RESOURCE_ODU_ADAPT_PROD,
  IPA_RM_RESOURCE_MHI_PROD,
  IPA_RM_RESOURCE_PROD_MAX,
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
  IPA_RM_RESOURCE_Q6_CONS = IPA_RM_RESOURCE_PROD_MAX,
  IPA_RM_RESOURCE_USB_CONS,
  IPA_RM_RESOURCE_USB_DPL_CONS,
  IPA_RM_RESOURCE_HSIC_CONS,
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
  IPA_RM_RESOURCE_WLAN_CONS,
  IPA_RM_RESOURCE_APPS_CONS,
  IPA_RM_RESOURCE_ODU_ADAPT_CONS,
  IPA_RM_RESOURCE_MHI_CONS,
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
  IPA_RM_RESOURCE_MAX
};
enum ipa_hw_type {
  IPA_HW_None = 0,
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
  IPA_HW_v1_0 = 1,
  IPA_HW_v1_1 = 2,
  IPA_HW_v2_0 = 3,
  IPA_HW_v2_1 = 4,
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
  IPA_HW_v2_5 = 5,
  IPA_HW_v2_6 = IPA_HW_v2_5,
  IPA_HW_v2_6L = 6,
  IPA_HW_v3_0 = 10,
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
  IPA_HW_v3_1 = 11,
  IPA_HW_MAX
};
struct ipa_rule_attrib {
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
  uint32_t attrib_mask;
  uint16_t src_port_lo;
  uint16_t src_port_hi;
  uint16_t dst_port_lo;
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
  uint16_t dst_port_hi;
  uint8_t type;
  uint8_t code;
  uint8_t tos_value;
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
  uint8_t tos_mask;
  uint32_t spi;
  uint16_t src_port;
  uint16_t dst_port;
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
  uint32_t meta_data;
  uint32_t meta_data_mask;
  uint8_t src_mac_addr[ETH_ALEN];
  uint8_t src_mac_addr_mask[ETH_ALEN];
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
  uint8_t dst_mac_addr[ETH_ALEN];
  uint8_t dst_mac_addr_mask[ETH_ALEN];
  uint16_t ether_type;
  union {
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
    struct {
      uint8_t tos;
      uint8_t protocol;
      uint32_t src_addr;
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
      uint32_t src_addr_mask;
      uint32_t dst_addr;
      uint32_t dst_addr_mask;
    } v4;
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
    struct {
      uint8_t tc;
      uint32_t flow_label;
      uint8_t next_hdr;
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
      uint32_t src_addr[4];
      uint32_t src_addr_mask[4];
      uint32_t dst_addr[4];
      uint32_t dst_addr_mask[4];
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
    } v6;
  } u;
};
#define IPA_IPFLTR_NUM_MEQ_32_EQNS 2
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
#define IPA_IPFLTR_NUM_IHL_MEQ_32_EQNS 2
#define IPA_IPFLTR_NUM_MEQ_128_EQNS 2
#define IPA_IPFLTR_NUM_IHL_RANGE_16_EQNS 2
struct ipa_ipfltr_eq_16 {
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
  int8_t offset;
  uint16_t value;
};
struct ipa_ipfltr_eq_32 {
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
  int8_t offset;
  uint32_t value;
};
struct ipa_ipfltr_mask_eq_128 {
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
  int8_t offset;
  uint8_t mask[16];
  uint8_t value[16];
};
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
struct ipa_ipfltr_mask_eq_32 {
  int8_t offset;
  uint32_t mask;
  uint32_t value;
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
};
struct ipa_ipfltr_range_eq_16 {
  int8_t offset;
  uint16_t range_low;
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
  uint16_t range_high;
};
struct ipa_ipfltri_rule_eq {
  uint16_t rule_eq_bitmap;
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
  uint8_t tos_eq_present;
  uint8_t tos_eq;
  uint8_t protocol_eq_present;
  uint8_t protocol_eq;
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
  uint8_t num_ihl_offset_range_16;
  struct ipa_ipfltr_range_eq_16 ihl_offset_range_16[IPA_IPFLTR_NUM_IHL_RANGE_16_EQNS];
  uint8_t num_offset_meq_32;
  struct ipa_ipfltr_mask_eq_32 offset_meq_32[IPA_IPFLTR_NUM_MEQ_32_EQNS];
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
  uint8_t tc_eq_present;
  uint8_t tc_eq;
  uint8_t fl_eq_present;
  uint32_t fl_eq;
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
  uint8_t ihl_offset_eq_16_present;
  struct ipa_ipfltr_eq_16 ihl_offset_eq_16;
  uint8_t ihl_offset_eq_32_present;
  struct ipa_ipfltr_eq_32 ihl_offset_eq_32;
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
  uint8_t num_ihl_offset_meq_32;
  struct ipa_ipfltr_mask_eq_32 ihl_offset_meq_32[IPA_IPFLTR_NUM_IHL_MEQ_32_EQNS];
  uint8_t num_offset_meq_128;
  struct ipa_ipfltr_mask_eq_128 offset_meq_128[IPA_IPFLTR_NUM_MEQ_128_EQNS];
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
  uint8_t metadata_meq32_present;
  struct ipa_ipfltr_mask_eq_32 metadata_meq32;
  uint8_t ipv4_frag_eq_present;
};
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
struct ipa_flt_rule {
  uint8_t retain_hdr;
  uint8_t to_uc;
  enum ipa_flt_action action;
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
  uint32_t rt_tbl_hdl;
  struct ipa_rule_attrib attrib;
  struct ipa_ipfltri_rule_eq eq_attrib;
  uint32_t rt_tbl_idx;
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
  uint8_t eq_attrib_type;
  uint8_t max_prio;
  uint8_t hashable;
  uint16_t rule_id;
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
};
enum ipa_hdr_l2_type {
  IPA_HDR_L2_NONE,
  IPA_HDR_L2_ETHERNET_II,
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
  IPA_HDR_L2_802_3,
  IPA_HDR_L2_MAX,
};
enum ipa_hdr_proc_type {
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
  IPA_HDR_PROC_NONE,
  IPA_HDR_PROC_ETHII_TO_ETHII,
  IPA_HDR_PROC_ETHII_TO_802_3,
  IPA_HDR_PROC_802_3_TO_ETHII,
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
  IPA_HDR_PROC_802_3_TO_802_3,
  IPA_HDR_PROC_MAX,
};
struct ipa_rt_rule {
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
  enum ipa_client_type dst;
  uint32_t hdr_hdl;
  uint32_t hdr_proc_ctx_hdl;
  struct ipa_rule_attrib attrib;
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
  uint8_t max_prio;
  uint8_t hashable;
  uint8_t retain_hdr;
};
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
struct ipa_hdr_add {
  char name[IPA_RESOURCE_NAME_MAX];
  uint8_t hdr[IPA_HDR_MAX_SIZE];
  uint8_t hdr_len;
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
  enum ipa_hdr_l2_type type;
  uint8_t is_partial;
  uint32_t hdr_hdl;
  int status;
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
  uint8_t is_eth2_ofst_valid;
  uint16_t eth2_ofst;
};
struct ipa_ioc_add_hdr {
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
  uint8_t commit;
  uint8_t num_hdrs;
  struct ipa_hdr_add hdr[0];
};
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
struct ipa_hdr_proc_ctx_add {
  enum ipa_hdr_proc_type type;
  uint32_t hdr_hdl;
  uint32_t proc_ctx_hdl;
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
  int status;
};
struct ipa_ioc_add_hdr_proc_ctx {
  uint8_t commit;
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
  uint8_t num_proc_ctxs;
  struct ipa_hdr_proc_ctx_add proc_ctx[0];
};
struct ipa_ioc_copy_hdr {
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
  char name[IPA_RESOURCE_NAME_MAX];
  uint8_t hdr[IPA_HDR_MAX_SIZE];
  uint8_t hdr_len;
  enum ipa_hdr_l2_type type;
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
  uint8_t is_partial;
  uint8_t is_eth2_ofst_valid;
  uint16_t eth2_ofst;
};
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
struct ipa_ioc_get_hdr {
  char name[IPA_RESOURCE_NAME_MAX];
  uint32_t hdl;
};
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
struct ipa_hdr_del {
  uint32_t hdl;
  int status;
};
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
struct ipa_ioc_del_hdr {
  uint8_t commit;
  uint8_t num_hdls;
  struct ipa_hdr_del hdl[0];
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
};
struct ipa_hdr_proc_ctx_del {
  uint32_t hdl;
  int status;
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
};
struct ipa_ioc_del_hdr_proc_ctx {
  uint8_t commit;
  uint8_t num_hdls;
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
  struct ipa_hdr_proc_ctx_del hdl[0];
};
struct ipa_rt_rule_add {
  struct ipa_rt_rule rule;
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
  uint8_t at_rear;
  uint32_t rt_rule_hdl;
  int status;
};
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
struct ipa_ioc_add_rt_rule {
  uint8_t commit;
  enum ipa_ip_type ip;
  char rt_tbl_name[IPA_RESOURCE_NAME_MAX];
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
  uint8_t num_rules;
  struct ipa_rt_rule_add rules[0];
};
struct ipa_ioc_add_rt_rule_after {
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
  uint8_t commit;
  enum ipa_ip_type ip;
  char rt_tbl_name[IPA_RESOURCE_NAME_MAX];
  uint8_t num_rules;
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
  uint32_t add_after_hdl;
  struct ipa_rt_rule_add rules[0];
};
struct ipa_rt_rule_mdfy {
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
  struct ipa_rt_rule rule;
  uint32_t rt_rule_hdl;
  int status;
};
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
struct ipa_ioc_mdfy_rt_rule {
  uint8_t commit;
  enum ipa_ip_type ip;
  uint8_t num_rules;
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
  struct ipa_rt_rule_mdfy rules[0];
};
struct ipa_rt_rule_del {
  uint32_t hdl;
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
  int status;
};
struct ipa_ioc_del_rt_rule {
  uint8_t commit;
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
  enum ipa_ip_type ip;
  uint8_t num_hdls;
  struct ipa_rt_rule_del hdl[0];
};
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
struct ipa_ioc_get_rt_tbl_indx {
  enum ipa_ip_type ip;
  char name[IPA_RESOURCE_NAME_MAX];
  uint32_t idx;
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
};
struct ipa_flt_rule_add {
  struct ipa_flt_rule rule;
  uint8_t at_rear;
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
  uint32_t flt_rule_hdl;
  int status;
};
struct ipa_ioc_add_flt_rule {
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
  uint8_t commit;
  enum ipa_ip_type ip;
  enum ipa_client_type ep;
  uint8_t global;
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
  uint8_t num_rules;
  struct ipa_flt_rule_add rules[0];
};
struct ipa_ioc_add_flt_rule_after {
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
  uint8_t commit;
  enum ipa_ip_type ip;
  enum ipa_client_type ep;
  uint8_t num_rules;
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
  uint32_t add_after_hdl;
  struct ipa_flt_rule_add rules[0];
};
struct ipa_flt_rule_mdfy {
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
  struct ipa_flt_rule rule;
  uint32_t rule_hdl;
  int status;
};
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
struct ipa_ioc_mdfy_flt_rule {
  uint8_t commit;
  enum ipa_ip_type ip;
  uint8_t num_rules;
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
  struct ipa_flt_rule_mdfy rules[0];
};
struct ipa_flt_rule_del {
  uint32_t hdl;
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
  int status;
};
struct ipa_ioc_del_flt_rule {
  uint8_t commit;
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
  enum ipa_ip_type ip;
  uint8_t num_hdls;
  struct ipa_flt_rule_del hdl[0];
};
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
struct ipa_ioc_get_rt_tbl {
  enum ipa_ip_type ip;
  char name[IPA_RESOURCE_NAME_MAX];
  uint32_t hdl;
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
};
struct ipa_ioc_query_intf {
  char name[IPA_RESOURCE_NAME_MAX];
  uint32_t num_tx_props;
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
  uint32_t num_rx_props;
  uint32_t num_ext_props;
  enum ipa_client_type excp_pipe;
};
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
struct ipa_ioc_tx_intf_prop {
  enum ipa_ip_type ip;
  struct ipa_rule_attrib attrib;
  enum ipa_client_type dst_pipe;
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
  enum ipa_client_type alt_dst_pipe;
  char hdr_name[IPA_RESOURCE_NAME_MAX];
  enum ipa_hdr_l2_type hdr_l2_type;
};
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
struct ipa_ioc_query_intf_tx_props {
  char name[IPA_RESOURCE_NAME_MAX];
  uint32_t num_tx_props;
  struct ipa_ioc_tx_intf_prop tx[0];
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
};
struct ipa_ioc_ext_intf_prop {
  enum ipa_ip_type ip;
  struct ipa_ipfltri_rule_eq eq_attrib;
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
  enum ipa_flt_action action;
  uint32_t rt_tbl_idx;
  uint8_t mux_id;
  uint32_t filter_hdl;
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
  uint8_t is_xlat_rule;
  uint32_t rule_id;
  uint8_t is_rule_hashable;
};
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
struct ipa_ioc_query_intf_ext_props {
  char name[IPA_RESOURCE_NAME_MAX];
  uint32_t num_ext_props;
  struct ipa_ioc_ext_intf_prop ext[0];
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
};
struct ipa_ioc_rx_intf_prop {
  enum ipa_ip_type ip;
  struct ipa_rule_attrib attrib;
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
  enum ipa_client_type src_pipe;
  enum ipa_hdr_l2_type hdr_l2_type;
};
struct ipa_ioc_query_intf_rx_props {
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
  char name[IPA_RESOURCE_NAME_MAX];
  uint32_t num_rx_props;
  struct ipa_ioc_rx_intf_prop rx[0];
};
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
struct ipa_ioc_nat_alloc_mem {
  char dev_name[IPA_RESOURCE_NAME_MAX];
  size_t size;
  off_t offset;
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
};
struct ipa_ioc_v4_nat_init {
  uint8_t tbl_index;
  uint32_t ipv4_rules_offset;
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
  uint32_t expn_rules_offset;
  uint32_t index_offset;
  uint32_t index_expn_offset;
  uint16_t table_entries;
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
  uint16_t expn_table_entries;
  uint32_t ip_addr;
};
struct ipa_ioc_v4_nat_del {
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
  uint8_t table_index;
  uint32_t public_ip_addr;
};
struct ipa_ioc_nat_dma_one {
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
  uint8_t table_index;
  uint8_t base_addr;
  uint32_t offset;
  uint16_t data;
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
};
struct ipa_ioc_nat_dma_cmd {
  uint8_t entries;
  struct ipa_ioc_nat_dma_one dma[0];
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
};
struct ipa_msg_meta {
  uint8_t msg_type;
  uint8_t rsvd;
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
  uint16_t msg_len;
};
struct ipa_wlan_msg {
  char name[IPA_RESOURCE_NAME_MAX];
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
  uint8_t mac_addr[IPA_MAC_ADDR_SIZE];
};
enum ipa_wlan_hdr_attrib_type {
  WLAN_HDR_ATTRIB_MAC_ADDR,
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
  WLAN_HDR_ATTRIB_STA_ID
};
struct ipa_wlan_hdr_attrib_val {
  enum ipa_wlan_hdr_attrib_type attrib_type;
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
  uint8_t offset;
  union {
    uint8_t mac_addr[IPA_MAC_ADDR_SIZE];
    uint8_t sta_id;
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
  } u;
};
struct ipa_wlan_msg_ex {
  char name[IPA_RESOURCE_NAME_MAX];
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
  uint8_t num_of_attribs;
  struct ipa_wlan_hdr_attrib_val attribs[0];
};
struct ipa_ecm_msg {
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
  char name[IPA_RESOURCE_NAME_MAX];
  int ifindex;
};
struct ipa_wan_msg {
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
  char upstream_ifname[IPA_RESOURCE_NAME_MAX];
  char tethered_ifname[IPA_RESOURCE_NAME_MAX];
  enum ipa_ip_type ip;
};
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
struct ipa_ioc_rm_dependency {
  enum ipa_rm_resource_name resource_name;
  enum ipa_rm_resource_name depends_on_name;
};
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
struct ipa_ioc_generate_flt_eq {
  enum ipa_ip_type ip;
  struct ipa_rule_attrib attrib;
  struct ipa_ipfltri_rule_eq eq_attrib;
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
};
struct ipa_ioc_write_qmapid {
  enum ipa_client_type client;
  uint8_t qmap_id;
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
};
enum ipacm_client_enum {
  IPACM_CLIENT_USB = 1,
  IPACM_CLIENT_WLAN,
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
  IPACM_CLIENT_MAX
};
#define IPA_IOC_ADD_HDR _IOWR(IPA_IOC_MAGIC, IPA_IOCTL_ADD_HDR, struct ipa_ioc_add_hdr *)
#define IPA_IOC_DEL_HDR _IOWR(IPA_IOC_MAGIC, IPA_IOCTL_DEL_HDR, struct ipa_ioc_del_hdr *)
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
#define IPA_IOC_ADD_RT_RULE _IOWR(IPA_IOC_MAGIC, IPA_IOCTL_ADD_RT_RULE, struct ipa_ioc_add_rt_rule *)
#define IPA_IOC_ADD_RT_RULE_AFTER _IOWR(IPA_IOC_MAGIC, IPA_IOCTL_ADD_RT_RULE_AFTER, struct ipa_ioc_add_rt_rule_after *)
#define IPA_IOC_DEL_RT_RULE _IOWR(IPA_IOC_MAGIC, IPA_IOCTL_DEL_RT_RULE, struct ipa_ioc_del_rt_rule *)
#define IPA_IOC_ADD_FLT_RULE _IOWR(IPA_IOC_MAGIC, IPA_IOCTL_ADD_FLT_RULE, struct ipa_ioc_add_flt_rule *)
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
#define IPA_IOC_ADD_FLT_RULE_AFTER _IOWR(IPA_IOC_MAGIC, IPA_IOCTL_ADD_FLT_RULE_AFTER, struct ipa_ioc_add_flt_rule_after *)
#define IPA_IOC_DEL_FLT_RULE _IOWR(IPA_IOC_MAGIC, IPA_IOCTL_DEL_FLT_RULE, struct ipa_ioc_del_flt_rule *)
#define IPA_IOC_COMMIT_HDR _IO(IPA_IOC_MAGIC, IPA_IOCTL_COMMIT_HDR)
#define IPA_IOC_RESET_HDR _IO(IPA_IOC_MAGIC, IPA_IOCTL_RESET_HDR)
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
#define IPA_IOC_COMMIT_RT _IOW(IPA_IOC_MAGIC, IPA_IOCTL_COMMIT_RT, enum ipa_ip_type)
#define IPA_IOC_RESET_RT _IOW(IPA_IOC_MAGIC, IPA_IOCTL_RESET_RT, enum ipa_ip_type)
#define IPA_IOC_COMMIT_FLT _IOW(IPA_IOC_MAGIC, IPA_IOCTL_COMMIT_FLT, enum ipa_ip_type)
#define IPA_IOC_RESET_FLT _IOW(IPA_IOC_MAGIC, IPA_IOCTL_RESET_FLT, enum ipa_ip_type)
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
#define IPA_IOC_DUMP _IO(IPA_IOC_MAGIC, IPA_IOCTL_DUMP)
#define IPA_IOC_GET_RT_TBL _IOWR(IPA_IOC_MAGIC, IPA_IOCTL_GET_RT_TBL, struct ipa_ioc_get_rt_tbl *)
#define IPA_IOC_PUT_RT_TBL _IOW(IPA_IOC_MAGIC, IPA_IOCTL_PUT_RT_TBL, uint32_t)
#define IPA_IOC_COPY_HDR _IOWR(IPA_IOC_MAGIC, IPA_IOCTL_COPY_HDR, struct ipa_ioc_copy_hdr *)
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
#define IPA_IOC_QUERY_INTF _IOWR(IPA_IOC_MAGIC, IPA_IOCTL_QUERY_INTF, struct ipa_ioc_query_intf *)
#define IPA_IOC_QUERY_INTF_TX_PROPS _IOWR(IPA_IOC_MAGIC, IPA_IOCTL_QUERY_INTF_TX_PROPS, struct ipa_ioc_query_intf_tx_props *)
#define IPA_IOC_QUERY_INTF_RX_PROPS _IOWR(IPA_IOC_MAGIC, IPA_IOCTL_QUERY_INTF_RX_PROPS, struct ipa_ioc_query_intf_rx_props *)
#define IPA_IOC_QUERY_INTF_EXT_PROPS _IOWR(IPA_IOC_MAGIC, IPA_IOCTL_QUERY_INTF_EXT_PROPS, struct ipa_ioc_query_intf_ext_props *)
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
#define IPA_IOC_GET_HDR _IOWR(IPA_IOC_MAGIC, IPA_IOCTL_GET_HDR, struct ipa_ioc_get_hdr *)
#define IPA_IOC_PUT_HDR _IOW(IPA_IOC_MAGIC, IPA_IOCTL_PUT_HDR, uint32_t)
#define IPA_IOC_ALLOC_NAT_MEM _IOWR(IPA_IOC_MAGIC, IPA_IOCTL_ALLOC_NAT_MEM, struct ipa_ioc_nat_alloc_mem *)
#define IPA_IOC_V4_INIT_NAT _IOWR(IPA_IOC_MAGIC, IPA_IOCTL_V4_INIT_NAT, struct ipa_ioc_v4_nat_init *)
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
#define IPA_IOC_NAT_DMA _IOWR(IPA_IOC_MAGIC, IPA_IOCTL_NAT_DMA, struct ipa_ioc_nat_dma_cmd *)
#define IPA_IOC_V4_DEL_NAT _IOWR(IPA_IOC_MAGIC, IPA_IOCTL_V4_DEL_NAT, struct ipa_ioc_v4_nat_del *)
#define IPA_IOC_GET_NAT_OFFSET _IOWR(IPA_IOC_MAGIC, IPA_IOCTL_GET_NAT_OFFSET, uint32_t *)
#define IPA_IOC_SET_FLT _IOW(IPA_IOC_MAGIC, IPA_IOCTL_SET_FLT, uint32_t)
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
#define IPA_IOC_PULL_MSG _IOWR(IPA_IOC_MAGIC, IPA_IOCTL_PULL_MSG, struct ipa_msg_meta *)
#define IPA_IOC_RM_ADD_DEPENDENCY _IOWR(IPA_IOC_MAGIC, IPA_IOCTL_RM_ADD_DEPENDENCY, struct ipa_ioc_rm_dependency *)
#define IPA_IOC_RM_DEL_DEPENDENCY _IOWR(IPA_IOC_MAGIC, IPA_IOCTL_RM_DEL_DEPENDENCY, struct ipa_ioc_rm_dependency *)
#define IPA_IOC_GENERATE_FLT_EQ _IOWR(IPA_IOC_MAGIC, IPA_IOCTL_GENERATE_FLT_EQ, struct ipa_ioc_generate_flt_eq *)
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
#define IPA_IOC_QUERY_EP_MAPPING _IOR(IPA_IOC_MAGIC, IPA_IOCTL_QUERY_EP_MAPPING, uint32_t)
#define IPA_IOC_QUERY_RT_TBL_INDEX _IOWR(IPA_IOC_MAGIC, IPA_IOCTL_QUERY_RT_TBL_INDEX, struct ipa_ioc_get_rt_tbl_indx *)
#define IPA_IOC_WRITE_QMAPID _IOWR(IPA_IOC_MAGIC, IPA_IOCTL_WRITE_QMAPID, struct ipa_ioc_write_qmapid *)
#define IPA_IOC_MDFY_FLT_RULE _IOWR(IPA_IOC_MAGIC, IPA_IOCTL_MDFY_FLT_RULE, struct ipa_ioc_mdfy_flt_rule *)
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
#define IPA_IOC_MDFY_RT_RULE _IOWR(IPA_IOC_MAGIC, IPA_IOCTL_MDFY_RT_RULE, struct ipa_ioc_mdfy_rt_rule *)
#define IPA_IOC_NOTIFY_WAN_UPSTREAM_ROUTE_ADD _IOWR(IPA_IOC_MAGIC, IPA_IOCTL_NOTIFY_WAN_UPSTREAM_ROUTE_ADD, struct ipa_wan_msg *)
#define IPA_IOC_NOTIFY_WAN_UPSTREAM_ROUTE_DEL _IOWR(IPA_IOC_MAGIC, IPA_IOCTL_NOTIFY_WAN_UPSTREAM_ROUTE_DEL, struct ipa_wan_msg *)
#define IPA_IOC_NOTIFY_WAN_EMBMS_CONNECTED _IOWR(IPA_IOC_MAGIC, IPA_IOCTL_NOTIFY_WAN_EMBMS_CONNECTED, struct ipa_wan_msg *)
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
#define IPA_IOC_ADD_HDR_PROC_CTX _IOWR(IPA_IOC_MAGIC, IPA_IOCTL_ADD_HDR_PROC_CTX, struct ipa_ioc_add_hdr_proc_ctx *)
#define IPA_IOC_DEL_HDR_PROC_CTX _IOWR(IPA_IOC_MAGIC, IPA_IOCTL_DEL_HDR_PROC_CTX, struct ipa_ioc_del_hdr_proc_ctx *)
#define IPA_IOC_GET_HW_VERSION _IOWR(IPA_IOC_MAGIC, IPA_IOCTL_GET_HW_VERSION, enum ipa_hw_type *)
#define TETH_BRIDGE_IOC_MAGIC 0xCE
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
#define TETH_BRIDGE_IOCTL_SET_BRIDGE_MODE 0
#define TETH_BRIDGE_IOCTL_SET_AGGR_PARAMS 1
#define TETH_BRIDGE_IOCTL_GET_AGGR_PARAMS 2
#define TETH_BRIDGE_IOCTL_GET_AGGR_CAPABILITIES 3
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
#define TETH_BRIDGE_IOCTL_MAX 4
enum teth_link_protocol_type {
  TETH_LINK_PROTOCOL_IP,
  TETH_LINK_PROTOCOL_ETHERNET,
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
  TETH_LINK_PROTOCOL_MAX,
};
enum teth_aggr_protocol_type {
  TETH_AGGR_PROTOCOL_NONE,
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
  TETH_AGGR_PROTOCOL_MBIM,
  TETH_AGGR_PROTOCOL_TLP,
  TETH_AGGR_PROTOCOL_MAX,
};
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
struct teth_aggr_params_link {
  enum teth_aggr_protocol_type aggr_prot;
  uint32_t max_transfer_size_byte;
  uint32_t max_datagrams;
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
};
struct teth_aggr_params {
  struct teth_aggr_params_link ul;
  struct teth_aggr_params_link dl;
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
};
struct teth_aggr_capabilities {
  uint16_t num_protocols;
  struct teth_aggr_params_link prot_caps[0];
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
};
struct teth_ioc_set_bridge_mode {
  enum teth_link_protocol_type link_protocol;
  uint16_t lcid;
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
};
struct teth_ioc_aggr_params {
  struct teth_aggr_params aggr_params;
  uint16_t lcid;
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
};
#define TETH_BRIDGE_IOC_SET_BRIDGE_MODE _IOW(TETH_BRIDGE_IOC_MAGIC, TETH_BRIDGE_IOCTL_SET_BRIDGE_MODE, struct teth_ioc_set_bridge_mode *)
#define TETH_BRIDGE_IOC_SET_AGGR_PARAMS _IOW(TETH_BRIDGE_IOC_MAGIC, TETH_BRIDGE_IOCTL_SET_AGGR_PARAMS, struct teth_ioc_aggr_params *)
#define TETH_BRIDGE_IOC_GET_AGGR_PARAMS _IOR(TETH_BRIDGE_IOC_MAGIC, TETH_BRIDGE_IOCTL_GET_AGGR_PARAMS, struct teth_ioc_aggr_params *)
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
#define TETH_BRIDGE_IOC_GET_AGGR_CAPABILITIES _IOWR(TETH_BRIDGE_IOC_MAGIC, TETH_BRIDGE_IOCTL_GET_AGGR_CAPABILITIES, struct teth_aggr_capabilities *)
#define ODU_BRIDGE_IOC_MAGIC 0xCD
#define ODU_BRIDGE_IOCTL_SET_MODE 0
#define ODU_BRIDGE_IOCTL_SET_LLV6_ADDR 1
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
#define ODU_BRIDGE_IOCTL_MAX 2
enum odu_bridge_mode {
  ODU_BRIDGE_MODE_ROUTER,
  ODU_BRIDGE_MODE_BRIDGE,
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
  ODU_BRIDGE_MODE_MAX,
};
#define ODU_BRIDGE_IOC_SET_MODE _IOW(ODU_BRIDGE_IOC_MAGIC, ODU_BRIDGE_IOCTL_SET_MODE, enum odu_bridge_mode)
#define ODU_BRIDGE_IOC_SET_LLV6_ADDR _IOW(ODU_BRIDGE_IOC_MAGIC, ODU_BRIDGE_IOCTL_SET_LLV6_ADDR, struct in6_addr *)
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
#endif

