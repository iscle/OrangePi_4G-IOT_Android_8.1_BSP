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
#ifndef _UAPI_MSM_IPC_H_
#define _UAPI_MSM_IPC_H_
#include <linux/types.h>
#include <linux/ioctl.h>
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
struct msm_ipc_port_addr {
  uint32_t node_id;
  uint32_t port_id;
};
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
struct msm_ipc_port_name {
  uint32_t service;
  uint32_t instance;
};
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
struct msm_ipc_addr {
  unsigned char addrtype;
  union {
    struct msm_ipc_port_addr port_addr;
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
    struct msm_ipc_port_name port_name;
  } addr;
};
#define MSM_IPC_WAIT_FOREVER (~0)
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
#ifndef AF_MSM_IPC
#define AF_MSM_IPC 27
#endif
#ifndef PF_MSM_IPC
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
#define PF_MSM_IPC AF_MSM_IPC
#endif
#define MSM_IPC_ADDR_NAME 1
#define MSM_IPC_ADDR_ID 2
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
struct sockaddr_msm_ipc {
  unsigned short family;
  struct msm_ipc_addr address;
  unsigned char reserved;
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
};
struct config_sec_rules_args {
  int num_group_info;
  uint32_t service_id;
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
  uint32_t instance_id;
  unsigned reserved;
  gid_t group_id[0];
};
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
#define IPC_ROUTER_IOCTL_MAGIC (0xC3)
#define IPC_ROUTER_IOCTL_GET_VERSION _IOR(IPC_ROUTER_IOCTL_MAGIC, 0, unsigned int)
#define IPC_ROUTER_IOCTL_GET_MTU _IOR(IPC_ROUTER_IOCTL_MAGIC, 1, unsigned int)
#define IPC_ROUTER_IOCTL_LOOKUP_SERVER _IOWR(IPC_ROUTER_IOCTL_MAGIC, 2, struct sockaddr_msm_ipc)
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
#define IPC_ROUTER_IOCTL_GET_CURR_PKT_SIZE _IOR(IPC_ROUTER_IOCTL_MAGIC, 3, unsigned int)
#define IPC_ROUTER_IOCTL_BIND_CONTROL_PORT _IOR(IPC_ROUTER_IOCTL_MAGIC, 4, unsigned int)
#define IPC_ROUTER_IOCTL_CONFIG_SEC_RULES _IOR(IPC_ROUTER_IOCTL_MAGIC, 5, struct config_sec_rules_args)
struct msm_ipc_server_info {
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
  uint32_t node_id;
  uint32_t port_id;
  uint32_t service;
  uint32_t instance;
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
};
struct server_lookup_args {
  struct msm_ipc_port_name port_name;
  int num_entries_in_array;
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
  int num_entries_found;
  uint32_t lookup_mask;
  struct msm_ipc_server_info srv_info[0];
};
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
#endif

