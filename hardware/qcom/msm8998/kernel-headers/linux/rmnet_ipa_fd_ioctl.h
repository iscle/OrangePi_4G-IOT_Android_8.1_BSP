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
#ifndef _RMNET_IPA_FD_IOCTL_H
#define _RMNET_IPA_FD_IOCTL_H
#include <linux/ioctl.h>
#include <linux/ipa_qmi_service_v01.h>
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
#include <linux/msm_ipa.h>
#define WAN_IOC_MAGIC 0x69
#define WAN_IOCTL_ADD_FLT_RULE 0
#define WAN_IOCTL_ADD_FLT_INDEX 1
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
#define WAN_IOCTL_VOTE_FOR_BW_MBPS 2
#define WAN_IOCTL_POLL_TETHERING_STATS 3
#define WAN_IOCTL_SET_DATA_QUOTA 4
#define WAN_IOCTL_SET_TETHER_CLIENT_PIPE 5
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
#define WAN_IOCTL_QUERY_TETHER_STATS 6
#define WAN_IOCTL_RESET_TETHER_STATS 7
#define WAN_IOCTL_QUERY_DL_FILTER_STATS 8
#define WAN_IOCTL_ADD_FLT_RULE_EX 9
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
#define WAN_IOCTL_QUERY_TETHER_STATS_ALL 10
#ifndef IFNAMSIZ
#define IFNAMSIZ 16
#endif
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
struct wan_ioctl_poll_tethering_stats {
  uint64_t polling_interval_secs;
  uint8_t reset_stats;
};
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
struct wan_ioctl_set_data_quota {
  char interface_name[IFNAMSIZ];
  uint64_t quota_mbytes;
  uint8_t set_quota;
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
};
struct wan_ioctl_set_tether_client_pipe {
  enum ipacm_client_enum ipa_client;
  uint8_t reset_client;
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
  uint32_t ul_src_pipe_len;
  uint32_t ul_src_pipe_list[QMI_IPA_MAX_PIPES_V01];
  uint32_t dl_dst_pipe_len;
  uint32_t dl_dst_pipe_list[QMI_IPA_MAX_PIPES_V01];
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
};
struct wan_ioctl_query_tether_stats {
  char upstreamIface[IFNAMSIZ];
  char tetherIface[IFNAMSIZ];
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
  enum ipacm_client_enum ipa_client;
  uint64_t ipv4_tx_packets;
  uint64_t ipv4_tx_bytes;
  uint64_t ipv4_rx_packets;
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
  uint64_t ipv4_rx_bytes;
  uint64_t ipv6_tx_packets;
  uint64_t ipv6_tx_bytes;
  uint64_t ipv6_rx_packets;
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
  uint64_t ipv6_rx_bytes;
};
struct wan_ioctl_query_tether_stats_all {
  char upstreamIface[IFNAMSIZ];
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
  enum ipacm_client_enum ipa_client;
  uint8_t reset_stats;
  uint64_t tx_bytes;
  uint64_t rx_bytes;
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
};
struct wan_ioctl_reset_tether_stats {
  char upstreamIface[IFNAMSIZ];
  uint8_t reset_stats;
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
};
struct wan_ioctl_query_dl_filter_stats {
  uint8_t reset_stats;
  struct ipa_get_data_stats_resp_msg_v01 stats_resp;
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
  uint32_t index;
};
#define WAN_IOC_ADD_FLT_RULE _IOWR(WAN_IOC_MAGIC, WAN_IOCTL_ADD_FLT_RULE, struct ipa_install_fltr_rule_req_msg_v01 *)
#define WAN_IOC_ADD_FLT_RULE_INDEX _IOWR(WAN_IOC_MAGIC, WAN_IOCTL_ADD_FLT_INDEX, struct ipa_fltr_installed_notif_req_msg_v01 *)
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
#define WAN_IOC_VOTE_FOR_BW_MBPS _IOWR(WAN_IOC_MAGIC, WAN_IOCTL_VOTE_FOR_BW_MBPS, uint32_t *)
#define WAN_IOC_POLL_TETHERING_STATS _IOWR(WAN_IOC_MAGIC, WAN_IOCTL_POLL_TETHERING_STATS, struct wan_ioctl_poll_tethering_stats *)
#define WAN_IOC_SET_DATA_QUOTA _IOWR(WAN_IOC_MAGIC, WAN_IOCTL_SET_DATA_QUOTA, struct wan_ioctl_set_data_quota *)
#define WAN_IOC_SET_TETHER_CLIENT_PIPE _IOWR(WAN_IOC_MAGIC, WAN_IOCTL_SET_TETHER_CLIENT_PIPE, struct wan_ioctl_set_tether_client_pipe *)
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
#define WAN_IOC_QUERY_TETHER_STATS _IOWR(WAN_IOC_MAGIC, WAN_IOCTL_QUERY_TETHER_STATS, struct wan_ioctl_query_tether_stats *)
#define WAN_IOC_RESET_TETHER_STATS _IOWR(WAN_IOC_MAGIC, WAN_IOCTL_RESET_TETHER_STATS, struct wan_ioctl_reset_tether_stats *)
#define WAN_IOC_QUERY_DL_FILTER_STATS _IOWR(WAN_IOC_MAGIC, WAN_IOCTL_QUERY_DL_FILTER_STATS, struct wan_ioctl_query_dl_filter_stats *)
#define WAN_IOC_ADD_FLT_RULE_EX _IOWR(WAN_IOC_MAGIC, WAN_IOCTL_ADD_FLT_RULE_EX, struct ipa_install_fltr_rule_req_ex_msg_v01 *)
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
#define WAN_IOC_QUERY_TETHER_STATS_ALL _IOWR(WAN_IOC_MAGIC, WAN_IOCTL_QUERY_TETHER_STATS_ALL, struct wan_ioctl_query_tether_stats_all *)
#endif

